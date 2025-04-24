/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.springframework.web.client.RestClient;

import com.google.errorprone.annotations.MustBeClosed;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch.ilm.Actions;
import co.elastic.clients.elasticsearch.ilm.Phase;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransportConfig;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import groovy.text.Template;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.jenkins.CredentialsNotFoundException;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import io.jenkins.plugins.opentelemetry.job.log.util.InputStreamByteBuffer;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIterator;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIteratorInputStream;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;


/**
 * Retrieve the logs from Elasticsearch.
 */
public class ElasticsearchLogStorageRetriever implements LogStorageRetriever, Closeable {

    private final static Logger logger = Logger.getLogger(ElasticsearchLogStorageRetriever.class.getName());

    // the duration in ms for which it is safe to keep the connection idle.
    public static final String KEEPALIVE_INTERVAL_DEFAULT = "30000"; /* 30 seconds */
    // keepalive enabled by default.
    public static final String KEEPALIVE_DEFAULT = "true";
    public static final String KEEPALIVE_INTERVAL_PROPERTY = ElasticsearchLogStorageRetriever.class.getName() + ".keepAlive.interval";
    public static final String KEEPALIVE_PROPERTY = ElasticsearchLogStorageRetriever.class.getName() + ".keepAlive.enabled";
    public static final int KEEPALIVE_INTERVAL = Integer.parseInt(System.getProperty(KEEPALIVE_INTERVAL_PROPERTY, KEEPALIVE_INTERVAL_DEFAULT));
    public static final boolean KEEPALIVE = Boolean.parseBoolean(System.getProperty(KEEPALIVE_PROPERTY, KEEPALIVE_DEFAULT));

    @NonNull
    private final Template buildLogsVisualizationUrlTemplate;

    private final TemplateBindingsProvider templateBindingsProvider;

    @NonNull
    final Credentials elasticsearchCredentials;
    @NonNull
    final String elasticsearchUrl;
    @NonNull
    final Rest5Client restClient;
    @NonNull
    final Rest5ClientTransport elasticsearchTransport;
    @NonNull
    private final ElasticsearchClient esClient;

    private Tracer _tracer;

    /**
     * TODO verify username:password auth vs apiKey auth
     */
    @MustBeClosed
    public ElasticsearchLogStorageRetriever(
        @NonNull String elasticsearchUrl, boolean disableSslVerifications,
        @NonNull Credentials elasticsearchCredentials,
        @NonNull Template buildLogsVisualizationUrlTemplate, @NonNull TemplateBindingsProvider templateBindingsProvider) {

        if (StringUtils.isBlank(elasticsearchUrl)) {
            throw new IllegalArgumentException("Elasticsearch url cannot be blank");
        }

        this.elasticsearchUrl = elasticsearchUrl;
        this.elasticsearchCredentials = elasticsearchCredentials;
        BasicCredentialsProvider credentialsProvider = getCredentialsProvider(elasticsearchUrl,
                elasticsearchCredentials);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionKeepAlive(TimeValue.ofMilliseconds(KEEPALIVE_INTERVAL))
            .build();
        CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .setDefaultCredentialsProvider(credentialsProvider)
            .build();

        if (disableSslVerifications) {
            SSLContext sslContext;
            try {
                sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustAllStrategy()).build();
            } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
                throw new IllegalArgumentException(e);
            }

            TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                .setSslContext(sslContext)
                .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
            PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
                .setTlsStrategy(tlsStrategy)
                .build();
            httpclient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(cm)
                .setDefaultCredentialsProvider(credentialsProvider)
                .build();
        }

        this.restClient = Rest5Client.builder(URI.create(elasticsearchUrl))
            .setHttpClient(httpclient)
            .build();

        this.elasticsearchTransport = new Rest5ClientTransport(restClient, new JacksonJsonpMapper());
        this.esClient = new ElasticsearchClient(elasticsearchTransport);

        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;
    }

    /**
     * Get the credentials provider for the Elasticsearch client.
     * @param elasticsearchUrl
     * @param elasticsearchCredentials
     * @return
     */
    private BasicCredentialsProvider getCredentialsProvider(String elasticsearchUrl,
            Credentials elasticsearchCredentials) {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        try{
            credentialsProvider.setCredentials(new AuthScope(HttpHost.create(elasticsearchUrl)), elasticsearchCredentials);
        } catch(URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        return credentialsProvider;
    }

    @NonNull
    @Override
    public LogsQueryResult overallLog(
        @NonNull String jobFullName, int runNumber, @NonNull String traceId, @NonNull String spanId, boolean complete, @NonNull Instant startTime, Instant endTime) {
        Charset charset = StandardCharsets.UTF_8;

        SpanBuilder spanBuilder = getTracer().spanBuilder("ElasticsearchLogStorageRetriever.overallLog")
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute("complete", complete);

        Span span = spanBuilder.startSpan();
        try (Scope scope = span.makeCurrent()) {
            LogLineIterator<Long> logLines = new ElasticsearchBuildLogsLineIterator(
                jobFullName, runNumber, traceId, esClient, getTracer());

            LogLineIterator.JenkinsHttpSessionLineBytesToLogLineIdMapper<Long> lineBytesToLineNumberConverter = new LogLineIterator.JenkinsHttpSessionLineBytesToLogLineIdMapper<>(jobFullName, runNumber, null);
            InputStream lineIteratorInputStream = new LogLineIteratorInputStream<>(logLines, lineBytesToLineNumberConverter, getTracer());
            ByteBuffer byteBuffer = new InputStreamByteBuffer(lineIteratorInputStream, getTracer());

            Map<String, Object> localBindings = Map.of(
                ObservabilityBackend.TemplateBindings.TRACE_ID, traceId,
                ObservabilityBackend.TemplateBindings.SPAN_ID, spanId);

            Map<String, Object> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings).getBindings();
            String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

            return new LogsQueryResult(
                byteBuffer,
                new LogsViewHeader(bindings.get(ElasticBackend.TemplateBindings.BACKEND_NAME).toString(), logsVisualizationUrl, bindings.get(ElasticBackend.TemplateBindings.BACKEND_24_24_ICON_URL).toString()),
                charset, complete
            );
        } finally {
            span.end();
        }
    }

    @NonNull
    @Override
    public LogsQueryResult stepLog(@NonNull String jobFullName, int runNumber, @NonNull String flowNodeId, @NonNull String traceId, @NonNull String spanId, boolean complete, @NonNull Instant startTime, @Nullable Instant endTime) {
        final Charset charset = StandardCharsets.UTF_8;

        SpanBuilder spanBuilder = getTracer().spanBuilder("ElasticsearchLogStorageRetriever.stepLog")
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, flowNodeId)
            .setAttribute("complete", complete);

        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {

            LogLineIterator<Long> logLines = new ElasticsearchBuildLogsLineIterator(
                jobFullName, runNumber, traceId, flowNodeId,
                esClient, getTracer());

            LogLineIterator.LogLineBytesToLogLineIdMapper<Long> logLineBytesToLogLineIdMapper = new LogLineIterator.JenkinsHttpSessionLineBytesToLogLineIdMapper<>(jobFullName, runNumber, flowNodeId);
            InputStream logLineIteratorInputStream = new LogLineIteratorInputStream<>(logLines, logLineBytesToLogLineIdMapper, getTracer());
            ByteBuffer byteBuffer = new InputStreamByteBuffer(logLineIteratorInputStream, getTracer());

            Map<String, Object> localBindings = new HashMap<>();
            localBindings.put(ObservabilityBackend.TemplateBindings.TRACE_ID, traceId);
            localBindings.put(ObservabilityBackend.TemplateBindings.SPAN_ID, spanId);

            Map<String, Object> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings).getBindings();
            String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

            return new LogsQueryResult(
                byteBuffer,
                new LogsViewHeader(
                    bindings.get(ElasticBackend.TemplateBindings.BACKEND_NAME).toString(),
                    logsVisualizationUrl,
                    bindings.get(ElasticBackend.TemplateBindings.BACKEND_24_24_ICON_URL).toString()),
                charset, complete
            );
        } finally {
            span.end();
        }
    }

    /**
     * Example of a successful check:
     * <pre>{@code
     * OK: Connected to Elasticsearch https://***.es.example.com with user 'jenkins'.
     * OK: Indices 'logs-*' found.
     * }</pre>
     */
    public List<FormValidation> checkElasticsearchSetup() {
        List<FormValidation> validations = new ArrayList<>();
        ElasticsearchIndicesClient indicesClient = this.esClient.indices();
        String elasticsearchUsername;
        try {
            elasticsearchUsername = Optional.ofNullable(elasticsearchCredentials.getUserPrincipal()).map(Principal::getName).orElse("No username for credentials type " + elasticsearchCredentials.getClass().getSimpleName());
        } catch (CredentialsNotFoundException e) {
            validations.add(FormValidation.error("No credentials defined"));
            return validations;
        }

        try {
            final GetIndexResponse response = indicesClient.get(b -> b.index(ElasticsearchFields.INDEX_TEMPLATE_PATTERNS));
            if (response == null || response.indices() == null || response.indices().isEmpty()) {
                validations.add(FormValidation.warning("Index Template '" + ElasticsearchFields.INDEX_TEMPLATE_PATTERNS + "' NOT found."));
            } else {
                validations.add(FormValidation.ok("Indices '" + ElasticsearchFields.INDEX_TEMPLATE_PATTERNS + "' found."));
            }
        } catch (ElasticsearchException e) {
            logger.fine(e.getLocalizedMessage());
            validations.addAll(findEsErrorCause(elasticsearchUsername, e));
            return validations;
        } catch (IOException e) {
            logger.fine(e.getLocalizedMessage());
            validations.add(FormValidation.warning("Exception accessing Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'.", e));
            return validations;
        }
        validations.add(FormValidation.ok("Connected to Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'."));
        return validations;
    }

    /**
     * Find the cause of the error in the Elasticsearch exception.
     * @param elasticsearchUsername the username used to connect to Elasticsearch
     * @param e the Elasticsearch exception
     * @return a list of FormValidation objects representing the error cause
     */
    @NonNull
    private List<FormValidation>  findEsErrorCause(@NonNull String elasticsearchUsername, @NonNull ElasticsearchException e) {
        List<FormValidation> validations = new ArrayList<>();
        ErrorCause errorCause = e.error();
        int status = e.status();
        if (ElasticsearchFields.ERROR_CAUSE_TYPE_SECURITY_EXCEPTION.equals(errorCause.type())) {
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                validations.add(FormValidation.error("Authentication failure " + "/" + status + " accessing Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'.", e));
            } else if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                validations.add(FormValidation.ok("Connected to Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'."));
                validations.add(FormValidation.warning(errorCause.type() + "/" + status + " accessing index template '" + ElasticsearchFields.INDEX_TEMPLATE_PATTERNS + "' on '" + elasticsearchUrl + "'. " +
                    "Elasticsearch user '" + elasticsearchUsername + "' doesn't have read permission to the index template metadata - " + errorCause.reason() + "."));
            } else {
                validations.add(FormValidation.ok("Connected to Elasticsearch " + elasticsearchUrl + " with user '" + elasticsearchUsername + "'."));
                validations.add(FormValidation.warning(errorCause.type() + "/" + status + " accessing index template '" + ElasticsearchFields.INDEX_TEMPLATE_PATTERNS + "' on '" + elasticsearchUrl + "' with " +
                    "Elasticsearch user '" + elasticsearchUsername + "' - " + errorCause.reason() + "."));
            }
        } else {
            validations.add(FormValidation.warning(errorCause.type() + "/" + status + " accessing index template '" + ElasticsearchFields.INDEX_TEMPLATE_PATTERNS + "' on '" + elasticsearchUrl + "' with " +
                "Elasticsearch user '" + elasticsearchUsername + "' - " + errorCause.reason() + "."));
        }
        return validations;
    }

    @NonNull
    protected static String prettyPrintPhaseRetentionPolicy(Phase phase, String phaseName) {
        if (phase == null) {
            return phaseName + " [phase not defined]";
        }
        String retentionPolicySpec = "no actions";
        Actions actions = phase.actions();
        if (actions != null){
            retentionPolicySpec = actions.toString();
        }
        return phaseName + "[" + retentionPolicySpec + "]";
    }

    @Override
    public void close() throws IOException {
        logger.log(Level.FINE, () -> "Shutdown Elasticsearch client...");
        this.elasticsearchTransport.close();
        this.restClient.close();
    }

    @Override
    public String toString() {
        return "ElasticsearchLogStorageRetriever{" +
            "elasticsearchUrl=" + elasticsearchUrl +
            '}';
    }

    private Tracer getTracer() {
        if (_tracer == null) {
            _tracer = JenkinsControllerOpenTelemetry.get().getDefaultTracer();
        }
        return _tracer;
    }
}