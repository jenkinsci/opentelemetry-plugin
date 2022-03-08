/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
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
import groovy.text.Template;
import hudson.security.ACL;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.job.RunFlowNodeIdentifier;
import io.jenkins.plugins.opentelemetry.job.RunIdentifier;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import io.jenkins.plugins.opentelemetry.job.log.util.StreamingByteBuffer;
import io.jenkins.plugins.opentelemetry.job.log.util.StreamingInputStream;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.Credentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpSession;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Retrieve the logs from Elasticsearch.
 */
public class ElasticsearchLogStorageRetriever implements LogStorageRetriever, Closeable {

    private final static Logger logger = Logger.getLogger(ElasticsearchLogStorageRetriever.class.getName());

    @Nonnull
    private final Template buildLogsVisualizationUrlTemplate;

    private final TemplateBindingsProvider templateBindingsProvider;

    @Nonnull
    final String elasticsearchUrl;

    @Nonnull
    final RestClientTransport elasticsearchTransport;
    @Nonnull
    private final ElasticsearchClient esClient;

    @Nonnull
    private final Tracer tracer;

    /**
     * TODO verify unsername:password auth vs apiKey auth
     */
    public ElasticsearchLogStorageRetriever(
        @Nonnull String elasticsearchUrl, @Nonnull Credentials elasticsearchCredentials,
        @Nonnull Template buildLogsVisualizationUrlTemplate, @Nonnull TemplateBindingsProvider templateBindingsProvider,
        @Nonnull Tracer tracer) {

        if (StringUtils.isBlank(elasticsearchUrl)) {
            throw new IllegalArgumentException("Elasticsearch url cannot be blank");
        }

        this.elasticsearchUrl = elasticsearchUrl;
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, elasticsearchCredentials);

        RestClient restClient = RestClient.builder(HttpHost.create(elasticsearchUrl))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        this.elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.esClient = new ElasticsearchClient(elasticsearchTransport);
        this.tracer = tracer;

        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;
    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(
        @Nonnull String jobFullName, int runNumber, @Nonnull String traceId, @Nonnull String spanId, boolean complete) {
        Charset charset = StandardCharsets.UTF_8;

        SpanBuilder spanBuilder = tracer.spanBuilder("ElasticsearchLogStorageRetriever.overallLog")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute("complete", complete);

        RunIdentifier runIdentifier = new RunIdentifier(jobFullName, runNumber);
        HttpSession session = Stapler.getCurrentRequest().getSession();
        ElasticsearchSearchContext context;
        context = (ElasticsearchSearchContext) session.getAttribute(runIdentifier.getId());
        if (context == null) {
            if (complete) {
                spanBuilder.setAttribute("elasticsearchSearchContext", "none");
            } else {
                context = new ElasticsearchSearchContext();
                session.setAttribute(runIdentifier.getId(), context);
                spanBuilder.setAttribute("elasticsearchSearchContext", "new");
            }
        } else {
            spanBuilder.setAttribute("from", context.from);
            if (complete) {
                session.removeAttribute(runIdentifier.getId());
                spanBuilder.setAttribute("elasticsearchSearchContext", "reuse-and-delete");
            } else {
                spanBuilder.setAttribute("elasticsearchSearchContext", "reuse");
            }
        }
        Span span = spanBuilder.startSpan();
        try (Scope scope = span.makeCurrent()) {
            Iterator<String> logLines = new ElasticsearchLogsSearchIterator(
                jobFullName, runNumber, traceId, context,
                esClient, tracer);

            StreamingInputStream streamingInputStream = new StreamingInputStream(logLines, complete, tracer);
            ByteBuffer byteBuffer = new StreamingByteBuffer(streamingInputStream, tracer);

            Map<String, String> localBindings = new HashMap<>();
            localBindings.put("traceId", traceId);
            localBindings.put("spanId", spanId);

            Map<String, String> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings).getBindings();
            String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

            return new LogsQueryResult(
                byteBuffer,
                new LogsViewHeader(bindings.get(ElasticBackend.TemplateBindings.BACKEND_NAME), logsVisualizationUrl, bindings.get(ElasticBackend.TemplateBindings.BACKEND_24_24_ICON_URL)),
                charset, complete
            );
        } finally {
            span.end();
        }
    }

    @Nonnull
    @Override
    public LogsQueryResult stepLog(@Nonnull String jobFullName, int runNumber, @Nonnull String flowNodeId, @Nonnull String traceId, @Nonnull String spanId, boolean complete) {
        final Charset charset = StandardCharsets.UTF_8;

        SpanBuilder spanBuilder = tracer.spanBuilder("ElasticsearchLogStorageRetriever.stepLog")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, flowNodeId)
            .setAttribute("complete", complete);

        RunFlowNodeIdentifier runFlowNodeIdentifier = new RunFlowNodeIdentifier(jobFullName, runNumber, flowNodeId);
        HttpSession session = Stapler.getCurrentRequest().getSession();
        ElasticsearchSearchContext context;
        context = (ElasticsearchSearchContext) session.getAttribute(runFlowNodeIdentifier.getId());
        if (context == null) {
            if (complete) {
                spanBuilder.setAttribute("elasticsearchSearchContext", "none");
            } else {
                context = new ElasticsearchSearchContext();
                session.setAttribute(runFlowNodeIdentifier.getId(), context);
                spanBuilder.setAttribute("elasticsearchSearchContext", "new");
            }
        } else {
            spanBuilder.setAttribute("from", context.from);
            if (complete) {
                session.removeAttribute(runFlowNodeIdentifier.getId());
                spanBuilder.setAttribute("elasticsearchSearchContext", "reuse-and-delete");
            } else {
                spanBuilder.setAttribute("elasticsearchSearchContext", "reuse");
            }
        }

        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {

            Iterator<String> logLines = new ElasticsearchLogsSearchIterator(
                jobFullName, runNumber, traceId, flowNodeId,
                context,
                esClient, tracer);

            StreamingInputStream streamingInputStream = new StreamingInputStream(logLines, complete, tracer);
            ByteBuffer byteBuffer = new StreamingByteBuffer(streamingInputStream, tracer);

            Map<String, String> localBindings = new HashMap<>();
            localBindings.put("traceId", traceId);
            localBindings.put("spanId", spanId);

            Map<String, String> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings).getBindings();
            String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

            return new LogsQueryResult(
                byteBuffer,
                new LogsViewHeader(bindings.get(ElasticBackend.TemplateBindings.BACKEND_NAME), logsVisualizationUrl, bindings.get(ElasticBackend.TemplateBindings.BACKEND_24_24_ICON_URL)),
                charset, complete
            );
        } finally {
            span.end();
        }
    }

    public List<FormValidation> checkElasticsearchSetup() throws IOException {
        List<FormValidation> validations = new ArrayList<>();
        ElasticsearchIndicesClient indicesClient = this.esClient.indices();

        // TODO remove workaround https://github.com/jenkinsci/opentelemetry-plugin/issues/336
        // we just check the existence of the Index Template and assume the Index Lifecycle Policy is "logs-apm.app_logs-default_policy"
        boolean indexTemplateExists;
        try {
            indexTemplateExists = indicesClient.existsIndexTemplate(b -> b.name(ElasticsearchFields.INDEX_TEMPLATE_NAME)).value();
        } catch (IOException e) {
            validations.add(FormValidation.warning("Exception accessing Elasticsearch " + elasticsearchUrl, e));
            return validations;
        }
        validations.add(FormValidation.ok("Connected to Elasticsearch " + elasticsearchUrl));

        if (indexTemplateExists) {
            validations.add(FormValidation.ok("Index Template '" + ElasticsearchFields.INDEX_TEMPLATE_NAME + "' found"));
        } else {
            validations.add(FormValidation.warning("Index Template '" + ElasticsearchFields.INDEX_TEMPLATE_NAME + "' NOT found"));
        }

        GetLifecycleResponse getLifecycleResponse = esClient.ilm().getLifecycle(b -> b.name(ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME));
        Lifecycle lifecyclePolicy = getLifecycleResponse.get(ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME);
        if (lifecyclePolicy == null) {
            validations.add(FormValidation.warning("Index Lifecycle Policy '" + ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME + "' NOT found"));
        } else {
            validations.add(FormValidation.ok("Index Lifecycle Policy '" + ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME + "' found"));
            Phases phases = lifecyclePolicy.policy().phases();
            List<String> retentionPolicy = new ArrayList<>();
            retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.hot(), "hot"));
            retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.warm(), "warm"));
            retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.cold(), "cold"));
            retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.delete(), "delete"));
            validations.add(FormValidation.ok("Logs retention policy: "
                + String.join(", ", retentionPolicy)));
        }
        return validations;
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
                .map(a -> Time.of(b -> b.time(a))).map(Time::time).orElse("Not defined");
            retentionPolicySpec.add("rollover[maxAge=" + maxAge + ", maxSize=" + maxSize + "]");
        }
        if (hotPhaseActions.containsKey("delete")) {
            String minAge = phase.minAge().time();
            retentionPolicySpec.add("delete[min_age=" + minAge + "]");
        }
        return phaseName + "[" + String.join(",", retentionPolicySpec) + "]";
    }

    @Override
    public void close() throws IOException {
        logger.log(Level.FINE, () -> "Shutdown Elasticsearch client...");
        this.elasticsearchTransport.close();
    }

    @Override
    public String toString() {
        return "ElasticsearchLogStorageRetriever{" +
            "buildLogsVisualizationUrlTemplate=" + buildLogsVisualizationUrlTemplate +
            ", templateBindingsProvider=" + templateBindingsProvider +
            '}';
    }

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