/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang.StringUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import com.google.errorprone.annotations.MustBeClosed;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.text.Template;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.GrafanaBackend;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.jenkins.HttpAuthHeaderFactory;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import io.jenkins.plugins.opentelemetry.job.log.util.InputStreamByteBuffer;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIterator;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIteratorInputStream;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.apachehttpclient.v5_2.ApacheHttpClientTelemetry;


public class LokiLogStorageRetriever implements LogStorageRetriever, Closeable {
    private final static Logger logger = Logger.getLogger(LokiLogStorageRetriever.class.getName());

    private final Template buildLogsVisualizationUrlTemplate;

    private final TemplateBindingsProvider templateBindingsProvider;

    private final String lokiUrl;
    private final String serviceName;
    private final Optional<String> serviceNamespace;
    private final Optional<HttpAuthHeaderFactory> httpAuthHeaderFactory;
    private final Optional<String> lokiTenantId;
    private final CloseableHttpClient httpClient;
    private final HttpContext httpContext;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    @MustBeClosed
    public LokiLogStorageRetriever(@Nonnull String lokiUrl, boolean disableSslVerifications,
                                   @Nonnull Optional<HttpAuthHeaderFactory> httpAuthHeaderFactory,
                                   @Nonnull Optional<String> lokiTenantId,
                                   @NonNull Template buildLogsVisualizationUrlTemplate, @NonNull TemplateBindingsProvider templateBindingsProvider,
                                   @Nonnull String serviceName, @Nonnull Optional<String> serviceNamespace) {
        if (StringUtils.isBlank(lokiUrl)) {
            throw new IllegalArgumentException("Loki url cannot be blank");
        }
        this.lokiUrl = lokiUrl;
        this.serviceName = serviceName;
        this.serviceNamespace = serviceNamespace;
        this.httpAuthHeaderFactory = httpAuthHeaderFactory;
        this.lokiTenantId = lokiTenantId;
        this.httpContext = HttpClientContext.create();
        this.openTelemetry = GlobalOpenTelemetry.get();
        this.tracer = openTelemetry.getTracer(ExtendedJenkinsAttributes.INSTRUMENTATION_NAME);

        HttpClientBuilder httpClientBuilder = ApacheHttpClientTelemetry.create(openTelemetry).newHttpClientBuilder();
        if (disableSslVerifications) {
            try {
                SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustAllStrategy()).build();
                SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
                PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslConnectionSocketFactory)
                    .build();
                httpClientBuilder.setConnectionManager(cm);
            } catch (GeneralSecurityException e) {
                logger.log(Level.WARNING, "IllegalStateException: failure to disable SSL certs verification");
            }
        }

        this.httpClient = httpClientBuilder.build();

        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;

    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(String jobFullName, int runNumber, String traceId, String spanId, boolean complete, Instant startTime, @Nullable Instant endTime) {
        SpanBuilder spanBuilder = tracer.spanBuilder("LokiLogStorageRetriever.overallLog")
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute("complete", complete);

        Span span = spanBuilder.startSpan();

        try (Scope ignored = span.makeCurrent()) {

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
                httpClient,
                httpContext,
                lokiUrl,
                httpAuthHeaderFactory,
                lokiTenantId,
                openTelemetry.getTracer( ExtendedJenkinsAttributes.INSTRUMENTATION_NAME));

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
    public LogsQueryResult stepLog(String jobFullName, int runNumber, String flowNodeId, String traceId, String spanId, boolean complete, Instant startTime, @Nullable Instant endTime) {
        SpanBuilder spanBuilder = tracer.spanBuilder("LokiLogStorageRetriever.stepLog")
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, flowNodeId)
            .setAttribute("complete", complete);

        Span span = spanBuilder.startSpan();

        try (Scope ignored = span.makeCurrent()) {

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
                lokiQueryParameters,
                httpClient,
                httpContext,
                lokiUrl,
                httpAuthHeaderFactory,
                lokiTenantId,
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
        ClassicHttpRequest lokiBuildInfoRequest = ClassicRequestBuilder.get().setUri(lokiUrl + "/loki/api/v1/format_query").addParameter("query", "{foo= \"bar\"}").build();

        httpAuthHeaderFactory.ifPresent(factory -> {
            // preemptive authentication due to a limitation of Grafana Cloud Logs (Loki) that doesn't return `WWW-Authenticate`
            // header to trigger traditional authentication
            lokiBuildInfoRequest.addHeader(factory.createAuthHeader());
        });
        lokiTenantId.ifPresent(tenantId -> lokiBuildInfoRequest.addHeader(new LokiTenantHeader(tenantId)));
        try {
            validations.add(httpClient.execute(lokiBuildInfoRequest, httpContext, new HttpClientResponseHandler<FormValidation>() {
                    @Override
                    public FormValidation handleResponse(ClassicHttpResponse lokiReadyResponse) {
                        FormValidation ret = FormValidation.ok("Loki connection successful");
                        try{
                            if (lokiReadyResponse.getCode() != 200) {
                                ret = FormValidation.error("Failure to access Loki (" + lokiBuildInfoRequest + "): " + EntityUtils.toString(lokiReadyResponse.getEntity()));
                            } else {
                                ret = FormValidation.ok("Loki connection successful");
                            }
                        } catch(ParseException | IOException e) {
                            ret = FormValidation.error("Failure to access Loki (" + lokiBuildInfoRequest + "): " + e.getMessage());
                        }
                        return ret;
                    }
                }));
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
