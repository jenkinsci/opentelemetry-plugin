/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.ilm.GetLifecycleResponse;
import co.elastic.clients.elasticsearch.ilm.Phase;
import co.elastic.clients.elasticsearch.ilm.Phases;
import co.elastic.clients.elasticsearch.ilm.get_lifecycle.Lifecycle;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import groovy.text.Template;
import hudson.security.ACL;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.Credentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * Retrieve the logs from Elasticsearch.
 * FIXME graceful shutdown
 */
public class ElasticsearchLogStorageRetriever implements LogStorageRetriever<ElasticsearchLogsQueryContext>, Closeable {
    /**
     * Field used by the Elastic-Otel mapping to store the {@link io.opentelemetry.sdk.logs.LogBuilder#setBody(String)}
     */
    public static final String FIELD_MESSAGE = "message";
    /**
     * Mapping for {@link SpanContext#getTraceId()}
     */
    public static final String FIELD_TRACE_ID = "trace.id";
    public static final String FIELD_TIMESTAMP = "@timestamp";

    public static final Time POINT_IN_TIME_KEEP_ALIVE = Time.of(builder -> builder.time("30s"));
    public static final int PAGE_SIZE = 100; // FIXME
    public static final String INDEX_TEMPLATE_PATTERNS = "logs-apm.app-*";
    public static final String INDEX_TEMPLATE_NAME = "logs-apm.app";
    /**
     * Waiting to fix https://github.com/jenkinsci/opentelemetry-plugin/issues/336 , we hard code the policy name
     */
    public static final String INDEX_LIFECYCLE_POLICY_NAME = "logs-apm.app_logs-default_policy";


    private final static Logger logger = Logger.getLogger(ElasticsearchLogStorageRetriever.class.getName());

    @Nonnull
    private final Template buildLogsVisualizationUrlTemplate;

    private final TemplateBindingsProvider templateBindingsProvider;

    @Nonnull
    final String elasticsearchUrl;
    @Nonnull
    private final ElasticsearchClient esClient;

    private final Tracer tracer;

    /**
     * TODO verify unsername:password auth vs apiKey auth
     */
    public ElasticsearchLogStorageRetriever(
        String elasticsearchUrl, Credentials elasticsearchCredentials,
        Template buildLogsVisualizationUrlTemplate, TemplateBindingsProvider templateBindingsProvider,
        Tracer tracer) {

        if (StringUtils.isBlank(elasticsearchUrl)) {
            throw new IllegalArgumentException("Elasticsearch url cannot be blank");
        }

        this.elasticsearchUrl = elasticsearchUrl;
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, elasticsearchCredentials);

        RestClient restClient = RestClient.builder(HttpHost.create(elasticsearchUrl))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        RestClientTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.esClient = new ElasticsearchClient(elasticsearchTransport);
        this.tracer = tracer;

        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;
    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(@Nonnull String traceId, @Nonnull String spanId, boolean complete, @Nullable ElasticsearchLogsQueryContext context) throws IOException {
        // https://www.elastic.co/guide/en/elasticsearch/reference/7.17/point-in-time-api.html
        Span span = tracer.spanBuilder("elasticsearch.search")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {

            final Charset charset = StandardCharsets.UTF_8;
            final boolean completed;
            final List<Hit<ObjectNode>> hits;

            final String pitId;
            final int pageNo;

            if (context == null) {
                // Initial request: open a point in time to have consistent pagination results
                pitId = esClient.openPointInTime(pit -> pit.index(INDEX_TEMPLATE_PATTERNS).keepAlive(POINT_IN_TIME_KEEP_ALIVE)).id();
                pageNo = 0;
            } else if (context.pitId == null) { // FIXME verify this behaviour
                logger.log(Level.FINE, () -> "Reset Elasticsearch query for unexpected closed Point In Time");
                span.setAttribute("info", "Reset Elasticsearch query for unexpected closed Point In Time");
                pitId = esClient.openPointInTime(pit -> pit.index(INDEX_TEMPLATE_PATTERNS).keepAlive(POINT_IN_TIME_KEEP_ALIVE)).id();
                pageNo = 0;
            } else if (complete) {
                // FIXME check algorithm
                // Get PIT id from context but reset the page number because complete=true
                pitId = context.pitId;
                pageNo = 0;
            } else {
                // Get PIT id and page number from context
                pitId = context.pitId;
                pageNo = context.pageNo;
            }

            span.setAttribute("query.index", INDEX_TEMPLATE_PATTERNS)
                .setAttribute("query.size", PAGE_SIZE)
                .setAttribute("pitId", pitId)
                .setAttribute("ci.pipeline.run.traceId", traceId)
                .setAttribute("ci.pipeline.run.spanId", spanId)
                .setAttribute("query.from", pageNo * PAGE_SIZE);

            SearchRequest searchRequest = new SearchRequest.Builder()
                .pit(pit -> pit.id(pitId).keepAlive(POINT_IN_TIME_KEEP_ALIVE))
                .from(pageNo * PAGE_SIZE)
                .size(PAGE_SIZE)
                .sort(s -> s.field(f -> f.field(FIELD_TIMESTAMP).order(SortOrder.Asc)))
                .query(q -> q.match(m -> m.field(FIELD_TRACE_ID).query(FieldValue.of(traceId))))
                // .fields() TODO narrow down the list fields to retrieve - we probably have to look at a source filter
                .build();

            logger.log(Level.FINE, "Retrieve logs for traceId: " + traceId);
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            // workaround https://github.com/elastic/elasticsearch-java/issues/163
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            SearchResponse<ObjectNode> searchResponse;
            try {
                searchResponse = this.esClient.search(searchRequest, ObjectNode.class);
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
            hits = searchResponse.hits().hits();
            span.setAttribute("results", hits.size());
            completed = hits.size() != PAGE_SIZE; // TODO is there smarter?

            if (completed) {
                logger.log(Level.FINE, () -> "Clear scrollId: " + pitId + " for trace: " + traceId + ", span: " + spanId);
                esClient.closePointInTime(p -> p.id(pitId));
            }

            ByteBuffer byteBuffer = new ByteBuffer();

            try (Writer w = new OutputStreamWriter(byteBuffer, charset)) {
                writeOutput(w, hits);
                span.setAttribute("results", hits.size());
                span.setAttribute("completed", completed);
            }

            String newPitId = completed ? null : pitId;
            logger.log(Level.FINE, () -> "overallLog(completed: " + completed + ", page: " + pageNo + ", written.length: " + byteBuffer.length() + ", pit.hash: " + pitId.hashCode() + ")");
            Map<String, String> localBindings = new HashMap<>();
            localBindings.put("traceId", traceId);
            localBindings.put("spanId", spanId);

            Map<String, String> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings).getBindings();
            String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

            logger.log(Level.FINE, () -> "overallLog(written.length: " + byteBuffer.length() + ")");
            return new LogsQueryResult(
                byteBuffer,
                new LogsViewHeader(bindings.get(ElasticBackend.TemplateBindings.BACKEND_NAME), logsVisualizationUrl, bindings.get(ElasticBackend.TemplateBindings.BACKEND_24_24_ICON_URL)),
                charset, completed,
                new ElasticsearchLogsQueryContext(newPitId, pageNo + 1)
            );
        } catch (IOException | RuntimeException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * FIXME implement
     */
    @Nonnull
    @Override
    public LogsQueryResult stepLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable ElasticsearchLogsQueryContext logsQueryContext) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void writeOutput(Writer writer, List<Hit<ObjectNode>> hits) throws IOException {
        for (Hit<ObjectNode> hit : hits) {
            ObjectNode source = hit.source();
            ObjectNode labels = (ObjectNode) source.findValue("labels");
            //Retrieve the label message and annotations to show the formatted message in Jenkins.
            JsonNode messageAsJsonNode = source.findValue(FIELD_MESSAGE);
            if (messageAsJsonNode == null) {
                logger.log(Level.FINE, () -> "Skip log with no message (document id: " + hit.id() + ")");
                continue;
            }
            String message = messageAsJsonNode.asText();

            JSONArray annotations;
            if (labels == null) {
                annotations = null;
            } else {
                JsonNode annotationsAsText = labels.get(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS.getKey());
                if (annotationsAsText == null) {
                    annotations = null;
                } else {
                    annotations = JSONArray.fromObject(annotationsAsText.asText());
                }
            }
            logger.log(Level.FINER, () -> "Write: " + message + ", id: " + hit.id());
            ConsoleNotes.write(writer, message, annotations);
        }
    }

    public List<FormValidation> checkElasticsearchSetup() throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        // workaround https://github.com/elastic/elasticsearch-java/issues/163
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            List<FormValidation> validations = new ArrayList<>();
            ElasticsearchIndicesClient indicesClient = this.esClient.indices();

            // TODO remove workaround https://github.com/jenkinsci/opentelemetry-plugin/issues/336
            // we just check the existence of the Index Template and assume the Index Lifecycle Policy is "logs-apm.app_logs-default_policy"
            boolean indexTemplateExists;
            try {
                indexTemplateExists = indicesClient.existsIndexTemplate(b -> b.name(INDEX_TEMPLATE_NAME)).value();
            } catch (IOException e) {
                validations.add(FormValidation.warning("Exception accessing Elasticsearch " + elasticsearchUrl, e));
                return validations;
            }
            validations.add(FormValidation.ok("Connected to Elasticsearch " + elasticsearchUrl));

            if (indexTemplateExists) {
                validations.add(FormValidation.ok("Index Template '" + INDEX_TEMPLATE_NAME + "' found"));
            } else {
                validations.add(FormValidation.warning("Index Template '" + INDEX_TEMPLATE_NAME + "' NOT found"));
            }

            GetLifecycleResponse getLifecycleResponse = esClient.ilm().getLifecycle(b -> b.name(INDEX_LIFECYCLE_POLICY_NAME));
            Lifecycle lifecyclePolicy = getLifecycleResponse.get(INDEX_LIFECYCLE_POLICY_NAME);
            if (lifecyclePolicy == null) {
                validations.add(FormValidation.warning("Index Lifecycle Policy '" + INDEX_LIFECYCLE_POLICY_NAME + "' NOT found"));
            } else {
                validations.add(FormValidation.ok("Index Lifecycle Policy '" + INDEX_LIFECYCLE_POLICY_NAME + "' found"));
                Phases phases = lifecyclePolicy.policy().phases();
                List<String> retentionPolicy = new ArrayList<>();
                retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.hot(), "hot"));
                retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.warm(), "warm"));
                retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.cold(), "cold"));
                retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.delete(), "delete"));
                validations.add(FormValidation.ok("Logs retention policy: "
                    + retentionPolicy.stream().collect(Collectors.joining(", "))));
            }
            return validations;
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    @Nonnull
    protected static String prettyPrintPhaseRetentionPolicy(Phase phase, String phaseName) {
        if (phase == null) {
            return phaseName + " [phase not defined]";
        }
        List<String> retentionPolicySpec = new ArrayList<>();
        JsonValue actionsAsJson = phase.actions().toJson();
        JsonObject hotPhaseActions = actionsAsJson.asJsonObject();
        if (hotPhaseActions.containsKey("rollover")) {
            JsonObject rollOver = hotPhaseActions.getJsonObject("rollover");
            String maxSize = rollOver.getString("max_size", "not defined");
            String maxAge = Optional
                .ofNullable(rollOver.getString("max_age", null))
                .map(a -> Time.of(b -> b.time(a))).map(t -> t.time()).orElse("Not defined");
            retentionPolicySpec.add("rollover[maxAge=" + maxAge + ", maxSize=" + maxSize + "]");
        }
        if (hotPhaseActions.containsKey("delete")) {
            String minAge = phase.minAge().time();
            retentionPolicySpec.add("delete[min_age=" + minAge + "]");
        }
        return phaseName + "[" + retentionPolicySpec.stream().collect(Collectors.joining(",")) + "]";
    }


    @Override
    public void close() throws IOException {
        logger.log(Level.INFO, () -> "Shutdown Elasticsearch client...");
        this.esClient.shutdown();
    }

    @Override
    public String toString() {
        return "ElasticsearchLogStorageRetriever{" +
            "buildLogsVisualizationUrlTemplate=" + buildLogsVisualizationUrlTemplate +
            ", templateBindingsProvider=" + templateBindingsProvider +
            '}';
    }

    /**
     * FIXME optimize search
     */
    public static Credentials getCredentials(String jenkinsCredentialsId) throws NoSuchElementException {
        UsernamePasswordCredentials usernamePasswordCredentials = (UsernamePasswordCredentials) CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.get(),
                ACL.SYSTEM, Collections.EMPTY_LIST),
            CredentialsMatchers.withId(jenkinsCredentialsId));

        if (usernamePasswordCredentials == null) {
            throw new NoSuchElementException("No credentials found for id '" + jenkinsCredentialsId + "' and type '" + UsernamePasswordCredentials.class.getName() + "'");
        }

        return new Credentials() {
            @Override
            public Principal getUserPrincipal() {
                return new BasicUserPrincipal(usernamePasswordCredentials.getUsername());
            }

            @Override
            public String getPassword() {
                return usernamePasswordCredentials.getPassword().getPlainText();
            }
        };
    }
}