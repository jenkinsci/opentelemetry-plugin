/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.ilm.GetLifecycleResponse;
import co.elastic.clients.elasticsearch.ilm.Phase;
import co.elastic.clients.elasticsearch.ilm.Phases;
import co.elastic.clients.elasticsearch.ilm.get_lifecycle.Lifecycle;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import groovy.text.Template;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.jenkins.CredentialsNotFoundException;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import io.jenkins.plugins.opentelemetry.job.log.util.InputStreamByteBuffer;
import io.jenkins.plugins.opentelemetry.job.log.util.LineIterator;
import io.jenkins.plugins.opentelemetry.job.log.util.LineIteratorInputStream;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    final Credentials elasticsearchCredentials;
    @Nonnull
    final String elasticsearchUrl;

    @Nonnull
    final RestClientTransport elasticsearchTransport;
    @Nonnull
    private final ElasticsearchClient esClient;

    private Tracer _tracer;

    /**
     * TODO verify unsername:password auth vs apiKey auth
     */
    public ElasticsearchLogStorageRetriever(
        @Nonnull String elasticsearchUrl, @Nonnull Credentials elasticsearchCredentials,
        @Nonnull Template buildLogsVisualizationUrlTemplate, @Nonnull TemplateBindingsProvider templateBindingsProvider) {

        if (StringUtils.isBlank(elasticsearchUrl)) {
            throw new IllegalArgumentException("Elasticsearch url cannot be blank");
        }

        this.elasticsearchUrl = elasticsearchUrl;
        this.elasticsearchCredentials = elasticsearchCredentials;
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, elasticsearchCredentials);

        RestClient restClient = RestClient.builder(HttpHost.create(elasticsearchUrl))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        this.elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.esClient = new ElasticsearchClient(elasticsearchTransport);

        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;
    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(
        @Nonnull String jobFullName, int runNumber, @Nonnull String traceId, @Nonnull String spanId, boolean complete) {
        Charset charset = StandardCharsets.UTF_8;

        SpanBuilder spanBuilder = getTracer().spanBuilder("ElasticsearchLogStorageRetriever.overallLog")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute("complete", complete);

        Span span = spanBuilder.startSpan();
        try (Scope scope = span.makeCurrent()) {
            LineIterator logLines = new ElasticsearchBuildLogsLineIterator(
                jobFullName, runNumber, traceId, esClient, getTracer());

            LineIterator.LineBytesToLineNumberConverter lineBytesToLineNumberConverter = new LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter(jobFullName, runNumber, null);
            LineIteratorInputStream lineIteratorInputStream = new LineIteratorInputStream(logLines, lineBytesToLineNumberConverter, getTracer());
            ByteBuffer byteBuffer = new InputStreamByteBuffer(lineIteratorInputStream, getTracer());

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

        SpanBuilder spanBuilder = getTracer().spanBuilder("ElasticsearchLogStorageRetriever.stepLog")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, flowNodeId)
            .setAttribute("complete", complete);

        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {

            LineIterator logLines = new ElasticsearchBuildLogsLineIterator(
                jobFullName, runNumber, traceId, flowNodeId,
                esClient, getTracer());
            LineIterator.LineBytesToLineNumberConverter lineBytesToLineNumberConverter = new LineIterator.JenkinsHttpSessionLineBytesToLineNumberConverter(jobFullName, runNumber, flowNodeId);

            LineIteratorInputStream lineIteratorInputStream = new LineIteratorInputStream(logLines, lineBytesToLineNumberConverter, getTracer());
            ByteBuffer byteBuffer = new InputStreamByteBuffer(lineIteratorInputStream, getTracer());

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

    /**
     * Example of a successful check:
     * <pre>{@code
     * OK: Verify existence of the Elasticsearch Index Template 'logs-apm.app' used to store Jenkins pipeline logs...
     * OK: Connected to Elasticsearch https://***.europe-west1.gcp.cloud.es.io:9243 with user 'jenkins'.
     * OK: Index Template 'logs-apm.app' found.
     * OK: Verify existence of the Index Lifecycle Management (ILM) Policy 'logs-apm.app' associated with the Index Template 'logs-apm.app' to define the time to live of the Jenkins pipeline logs in Elasticsearch...
     * OK: Index Lifecycle Policy 'logs-apm.app_logs-default_policy' found.
     * OK: Logs retention policy: hot[rollover[maxAge=30d, maxSize=50gb]], warm [phase not defined], cold [phase not defined], delete[delete[min_age=10d]].
     * }</pre>
     */
    public List<FormValidation> checkElasticsearchSetup() throws IOException {
        List<FormValidation> validations = new ArrayList<>();
        ElasticsearchIndicesClient indicesClient = this.esClient.indices();
        String elasticsearchUsername;
        try {
            elasticsearchUsername = Optional.ofNullable(elasticsearchCredentials.getUserPrincipal()).map(p -> p.getName()).orElse("No username for credentials type " + elasticsearchCredentials.getClass().getSimpleName());
        } catch (CredentialsNotFoundException e) {
            validations.add(FormValidation.error("No credentials defined"));
            return validations;
        }


        // TODO remove workaround https://github.com/jenkinsci/opentelemetry-plugin/issues/336
        // we just check the existence of the Index Template and assume the Index Lifecycle Policy is "logs-apm.app_logs-default_policy"

        validations.add(FormValidation.ok("Verify existence of the Elasticsearch Index Template '" + ElasticsearchFields.INDEX_TEMPLATE_NAME + "' used to store Jenkins pipeline logs..."));

        boolean indexTemplateExists;
        try {
            indexTemplateExists = indicesClient.existsIndexTemplate(b -> b.name(ElasticsearchFields.INDEX_TEMPLATE_NAME)).value();
        } catch (ElasticsearchException e) {
            ErrorCause errorCause = e.error();
            int status = e.status();
            if (ElasticsearchFields.ERROR_CAUSE_TYPE_SECURITY_EXCEPTION.equals(errorCause.type())) {
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    validations.add(FormValidation.error("Authentication failure " + "/" + status + " accessing Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'.", e));
                } else if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                    validations.add(FormValidation.ok("Connected to Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'."));
                    validations.add(FormValidation.warning(errorCause.type() + "/" + status + " accessing index template '" + ElasticsearchFields.INDEX_TEMPLATE_NAME + "' on '" + elasticsearchUrl + "'. " +
                        "Elasticsearch user '" + elasticsearchUsername + "' doesn't have read permission to the index template metadata - " + errorCause.reason() + "."));
                } else {
                    validations.add(FormValidation.ok("Connected to Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'."));
                    validations.add(FormValidation.warning(errorCause.type() + "/" + status + " accessing index template '" + ElasticsearchFields.INDEX_TEMPLATE_NAME + "' on '" + elasticsearchUrl + "' with " +
                        "Elasticsearch user '" + elasticsearchUsername + "' - " + errorCause.reason() + "."));
                }
            } else {
                validations.add(FormValidation.warning(errorCause.type() + "/" + status + " accessing index template '" + ElasticsearchFields.INDEX_TEMPLATE_NAME + "' on '" + elasticsearchUrl + "' with " +
                    "Elasticsearch user '" + elasticsearchUsername + "' - " + errorCause.reason() + "."));
            }
            return validations;
        } catch (IOException e) {
            validations.add(FormValidation.warning("Exception accessing Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'.", e));
            return validations;
        }
        validations.add(FormValidation.ok("Connected to Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'."));

        if (indexTemplateExists) {
            validations.add(FormValidation.ok("Index Template '" + ElasticsearchFields.INDEX_TEMPLATE_NAME + "' found."));
        } else {
            validations.add(FormValidation.warning("Index Template '" + ElasticsearchFields.INDEX_TEMPLATE_NAME + "' NOT found."));
        }

        validations.add(FormValidation.ok("Verify existence of the Index Lifecycle Management (ILM) Policy '" + ElasticsearchFields.INDEX_TEMPLATE_NAME + "' associated with the Index Template '" + ElasticsearchFields.INDEX_TEMPLATE_NAME +
            "' to define the time to live of the Jenkins pipeline logs in Elasticsearch..."));

        GetLifecycleResponse getLifecycleResponse;
        try {
            getLifecycleResponse = esClient.ilm().getLifecycle(b -> b.name(ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME));
        } catch (ElasticsearchException e) {
            ErrorCause errorCause = e.error();
            int status = e.status();
            if (ElasticsearchFields.ERROR_CAUSE_TYPE_SECURITY_EXCEPTION.equals(errorCause.type())) {
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    validations.add(FormValidation.error("Authentication failure " + "/" + status + " accessing Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'.", e));
                } else if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                    validations.add(FormValidation.ok(
                        "Time to live of the pipeline logs in Elasticsearch " + elasticsearchUrl + "not available. " +
                            "The Index Lifecycle Management (ILM) policy '" + ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME + "' is not readable by the Elasticsearch user '" + elasticsearchUsername + ". " +
                            " Details: " + errorCause.type() + " - " + errorCause.reason() + "."));
                } else {
                    validations.add(FormValidation.warning(errorCause.type() + "/" + status + " accessing lifecycle policy '" + ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME + "': " + errorCause.reason() + "."));
                }
            } else {
                validations.add(FormValidation.warning(errorCause.type() + "/" + status + " accessing lifecycle policy '" + ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME + "': " + errorCause.reason() + "."));
            }
            return validations;
        } catch (IOException e) {
            validations.add(FormValidation.warning("Exception accessing lifecycle policy '" + ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME + "'.", e));
            return validations;
        }
        Lifecycle lifecyclePolicy = getLifecycleResponse.get(ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME);
        if (lifecyclePolicy == null) {
            validations.add(FormValidation.warning("Index Lifecycle Policy '" + ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME + "' NOT found."));
        } else {
            validations.add(FormValidation.ok("Index Lifecycle Policy '" + ElasticsearchFields.INDEX_LIFECYCLE_POLICY_NAME + "' found."));
            Phases phases = lifecyclePolicy.policy().phases();
            List<String> retentionPolicy = new ArrayList<>();
            retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.hot(), "hot"));
            retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.warm(), "warm"));
            retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.cold(), "cold"));
            retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.delete(), "delete"));
            validations.add(FormValidation.ok("Logs retention policy: " + String.join(", ", retentionPolicy) + "."));
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

    private Tracer getTracer(){
        if (_tracer == null) {
            _tracer = OpenTelemetrySdkProvider.get().getTracer();
        }
        return _tracer;
    }
}