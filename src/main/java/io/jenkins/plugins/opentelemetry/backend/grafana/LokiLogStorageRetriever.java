/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.text.Template;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.GrafanaBackend;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import io.jenkins.plugins.opentelemetry.job.log.util.InputStreamByteBuffer;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIterator;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIteratorInputStream;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.apachehttpclient.v4_3.ApacheHttpClientTelemetry;
import org.apache.commons.lang.StringUtils;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


public class LokiLogStorageRetriever implements LogStorageRetriever, Closeable {
    private final static Logger logger = Logger.getLogger(LokiLogStorageRetriever.class.getName());

    private final Template buildLogsVisualizationUrlTemplate;

    private final TemplateBindingsProvider templateBindingsProvider;

    private final String lokiUrl;
    private final String serviceName;
    private final Optional<String> serviceNamespace;
    private final Optional<Credentials> lokiCredentials;
    private final Optional<String> lokiTenantId;
    private final CloseableHttpClient httpClient;
    private final HttpContext httpContext;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    @MustBeClosed
    public LokiLogStorageRetriever(@Nonnull String lokiUrl, boolean disableSslVerifications,
                                   @Nonnull Optional<Credentials> lokiCredentials,
                                   @Nonnull Optional<String> lokiTenantId,
                                   @NonNull Template buildLogsVisualizationUrlTemplate, @NonNull TemplateBindingsProvider templateBindingsProvider,
                                   @Nonnull String serviceName, @Nonnull Optional<String> serviceNamespace) {
        if (StringUtils.isBlank(lokiUrl)) {
            throw new IllegalArgumentException("Loki url cannot be blank");
        }
        this.lokiUrl = lokiUrl;
        this.serviceName = serviceName;
        this.serviceNamespace = serviceNamespace;
        this.lokiCredentials = lokiCredentials;
        this.lokiTenantId = lokiTenantId;
        this.httpContext = new BasicHttpContext();
        this.openTelemetry = GlobalOpenTelemetry.get();
        this.tracer = openTelemetry.getTracer(JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME);

        HttpClientBuilder httpClientBuilder = ApacheHttpClientTelemetry.create(openTelemetry).newHttpClientBuilder();
        if (disableSslVerifications) {
            try {
                SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustAllStrategy()).build();
                httpClientBuilder.setSSLContext(sslContext);
            } catch (GeneralSecurityException e) {
                logger.log(Level.WARNING, "IllegalStateException: failure to disable SSL certs verification");
            }
            httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }

        this.httpClient = httpClientBuilder.build();

        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;

    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(@Nonnull String jobFullName, int runNumber, @Nonnull String traceId, @Nonnull String spanId, boolean complete, @Nonnull Instant startTime, @Nullable Instant endTime) {
        SpanBuilder spanBuilder = tracer.spanBuilder("LokiLogStorageRetriever.overallLog")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute("complete", complete);

        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {

            LokiGetJenkinsBuildLogsQueryParameters lokiQueryParameters = new LokiGetJenkinsBuildLogsQueryParametersBuilder()
                .setJobFullName(jobFullName)
                .setRunNumber(runNumber)
                .setTraceId(traceId)
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setServiceName(serviceName)
                .setServiceNamespace(serviceNamespace)
                .build();
            LogLineIterator<Long> logLines = new LokiBuildLogsLineIterator(
                lokiQueryParameters,
                httpClient, httpContext,
                lokiUrl, lokiCredentials, lokiTenantId,
                openTelemetry.getTracer( JenkinsOtelSemanticAttributes.INSTRUMENTATION_NAME));

            LogLineIterator.JenkinsHttpSessionLineBytesToLogLineIdMapper<Long> lineBytesToLineNumberConverter = new LogLineIterator.JenkinsHttpSessionLineBytesToLogLineIdMapper<>(jobFullName, runNumber, null);
            InputStream lineIteratorInputStream = new LogLineIteratorInputStream<>(logLines, lineBytesToLineNumberConverter, tracer);
            ByteBuffer byteBuffer = new InputStreamByteBuffer(lineIteratorInputStream, tracer);

            Map<String, Object> localBindings = Map.of(
                ObservabilityBackend.TemplateBindings.TRACE_ID, traceId,
                ObservabilityBackend.TemplateBindings.SPAN_ID, spanId,
                ObservabilityBackend.TemplateBindings.START_TIME, startTime,
                ObservabilityBackend.TemplateBindings.END_TIME, Optional.ofNullable(endTime).or(() -> Optional.of(Instant.now())).get()
            );

            Map<String, Object> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings).getBindings();
            String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

            return new LogsQueryResult(
                byteBuffer,
                new LogsViewHeader(
                    bindings.get(GrafanaBackend.TemplateBindings.BACKEND_NAME).toString(),
                    logsVisualizationUrl,
                    bindings.get(GrafanaBackend.TemplateBindings.BACKEND_24_24_ICON_URL).toString()),
                StandardCharsets.UTF_8, complete
            );
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @Nonnull
    @Override
    public LogsQueryResult stepLog(@Nonnull String jobFullName, int runNumber, @Nonnull String flowNodeId, @Nonnull String traceId, @Nonnull String spanId, boolean complete, @Nonnull Instant startTime, @Nullable Instant endTime) {
        SpanBuilder spanBuilder = tracer.spanBuilder("LokiLogStorageRetriever.stepLog")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, flowNodeId)
            .setAttribute("complete", complete);

        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {

            LokiGetJenkinsBuildLogsQueryParameters lokiQueryParameters = new LokiGetJenkinsBuildLogsQueryParametersBuilder()
                .setJobFullName(jobFullName)
                .setRunNumber(runNumber)
                .setTraceId(traceId)
                .setFlowNodeId(flowNodeId)
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setServiceName(serviceName)
                .setServiceNamespace(serviceNamespace)
                .build();
            LogLineIterator<Long> logLines = new LokiBuildLogsLineIterator(
                lokiQueryParameters, httpClient, httpContext,
                lokiUrl, lokiCredentials, lokiTenantId,
                openTelemetry.getTracer("io.jenkins"));

            LogLineIterator.LogLineBytesToLogLineIdMapper<Long> logLineBytesToLogLineIdMapper = new LogLineIterator.JenkinsHttpSessionLineBytesToLogLineIdMapper<>(jobFullName, runNumber, null);
            InputStream logLineIteratorInputStream = new LogLineIteratorInputStream<>(logLines, logLineBytesToLogLineIdMapper, tracer);
            ByteBuffer byteBuffer = new InputStreamByteBuffer(logLineIteratorInputStream, tracer);

            Map<String, Object> localBindings = Map.of(
                ObservabilityBackend.TemplateBindings.TRACE_ID, traceId,
                ObservabilityBackend.TemplateBindings.SPAN_ID, spanId,
                ObservabilityBackend.TemplateBindings.START_TIME, startTime,
                ObservabilityBackend.TemplateBindings.END_TIME, Optional.ofNullable(endTime).or(() -> Optional.of(Instant.now())).get()
            );

            Map<String, Object> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings).getBindings();
            String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

            return new LogsQueryResult(
                byteBuffer,
                new LogsViewHeader(
                    bindings.get(GrafanaBackend.TemplateBindings.BACKEND_NAME).toString(),
                    logsVisualizationUrl,
                    bindings.get(GrafanaBackend.TemplateBindings.BACKEND_24_24_ICON_URL).toString()),
                StandardCharsets.UTF_8, complete
            );
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    public List<FormValidation> checkLokiSetup() {
        List<FormValidation> validations = new ArrayList<>();

        // `/ready`and `/loki/api/v1/status/buildinfo` return a 404 on Grafana Cloud, use the format_query request instead
        HttpUriRequest lokiBuildInfoRequest = RequestBuilder.get().setUri(lokiUrl + "/loki/api/v1/format_query").addParameter("query", "{foo= \"bar\"}").build();

        lokiCredentials.ifPresent(lokiCredentials -> {
            try {
                // preemptive authentication due to a limitation of Grafana Cloud Logs (Loki) that doesn't return `WWW-Authenticate`
                // header to trigger traditional authentication
                lokiBuildInfoRequest.addHeader(new BasicScheme().authenticate(lokiCredentials, lokiBuildInfoRequest, httpContext));
            } catch (AuthenticationException e) {
                throw new RuntimeException(e);
            }
        });
        lokiTenantId.ifPresent(tenantId -> lokiBuildInfoRequest.addHeader(new LokiTenantHeader(tenantId)));
        try (CloseableHttpResponse lokiReadyResponse = httpClient.execute(lokiBuildInfoRequest, httpContext)) {
            if (lokiReadyResponse.getStatusLine().getStatusCode() != 200) {
                validations.add(FormValidation.error("Failure to access Loki (" + lokiBuildInfoRequest + "): " + EntityUtils.toString(lokiReadyResponse.getEntity())));
            } else {
                validations.add(FormValidation.ok("Loki connection successful"));
            }
        } catch (IOException e) {
            validations.add(FormValidation.error("Failure to access Loki (" + lokiBuildInfoRequest + "): " + e.getMessage()));
        }
        return validations;
    }

    @Override
    public void close() throws IOException {
        this.httpClient.close();
    }
}
