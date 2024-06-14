/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import static io.jenkins.plugins.opentelemetry.backend.grafana.LokiMetadata.*;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;
import com.jayway.jsonpath.JsonPath;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.plugins.opentelemetry.job.log.util.CloseableIterator;
import io.jenkins.plugins.opentelemetry.job.log.util.LineIterator;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * HttpClient can't do preemptive auth and Loki doesn't return `WWW-Authenticate` header when authentication is
 * needed so use Apache HTTP Client instead.
 */
public class LokiBuildLogsLineIterator implements LineIterator, AutoCloseable {

    private final static Logger logger = Logger.getLogger(LokiBuildLogsLineIterator.class.getName());
    public final static int MAX_LINES = Integer.MAX_VALUE;
    public final static int MAX_QUERIES = 100;
    public final static int MAX_LINES_PER_REQUEST = 1_000;

    final String jobFullName;
    final int runNumber;
    final Optional<String> flowNodeId;
    final String traceId;

    final String serviceName;
    final Optional<String> serviceNamespace;

    final String lokiUrl;
    final Optional<Credentials> lokiCredentials;
    final Optional<String> lokiTenantId;

    final CloseableHttpClient httpClient;
    final HttpContext httpContext;

    long startTimeInNanos;
    final Optional<Long> endTimeInNanos;

    long lineCounter;

    @VisibleForTesting
    int queryCounter;

    final Tracer tracer;

    Iterator<String> delegate;
    boolean endOfStream;

    public LokiBuildLogsLineIterator(
        @NonNull String jobFullName,
        int runNumber,
        @NonNull String traceId,
        @NonNull Optional<String> flowNodeId,

        @NonNull Instant startTimeInNanos,
        @NonNull Optional<Instant> endTime,

        @NonNull String serviceName,
        @NonNull Optional<String> serviceNamespace,

        @NonNull CloseableHttpClient httpClient,
        @NonNull HttpContext httpContext,

        @NonNull String lokiUrl,
        @NonNull Optional<Credentials> lokiCredentials,
        @Nullable Optional<String> lokiTenantId,
        @NonNull Tracer tracer) {

        this.jobFullName = jobFullName;
        this.runNumber = runNumber;
        this.traceId = traceId;
        this.flowNodeId = flowNodeId;

        this.startTimeInNanos = TimeUnit.NANOSECONDS.convert(startTimeInNanos.toEpochMilli(), TimeUnit.MILLISECONDS) + startTimeInNanos.getNano();
        this.endTimeInNanos = endTime.map(end -> TimeUnit.NANOSECONDS.convert(end.toEpochMilli(), TimeUnit.MILLISECONDS) + end.getNano());

        this.serviceName = serviceName;
        this.serviceNamespace = serviceNamespace;

        this.lokiUrl = lokiUrl;
        this.lokiCredentials = lokiCredentials;
        this.lokiTenantId = lokiTenantId;

        this.httpClient = httpClient;
        this.httpContext = httpContext;

        this.tracer = tracer;
    }

    @NonNull
    Iterator<String> getCurrentIterator() {
        try {
            if (endOfStream) {
                // don't try to load more
                return delegate;
            }
            if (delegate == null) {
                delegate = loadNextLogLines();
            }
            if (delegate.hasNext()) {
                return delegate;
            }
            delegate = loadNextLogLines();
            if (lineCounter > MAX_LINES) {
                delegate = Iterators.concat(delegate, Collections.singleton("...").iterator());
                endOfStream = true;
            } else if (!delegate.hasNext()) {
                endOfStream = true;
            }
            return delegate;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Iterator<String> loadNextLogLines() throws IOException {
        if (lineCounter > MAX_LINES || queryCounter > MAX_QUERIES) {
            logger.log(Level.INFO, () -> "Circuit breaker: " + lineCounter + " log lines read in " + queryCounter
                + " queries for " + jobFullName + " #" + runNumber
                + " traceId: " + traceId + " flowNodeId: " + flowNodeId
                + " startTime: " + startTimeInNanos + " endTime: " + endTimeInNanos);
            return Collections.emptyIterator();
        }

        // https://grafana.com/docs/loki/latest/reference/loki-http-api/#query-logs-within-a-range-of-time

        final StringBuilder logQl = new StringBuilder("{");
        serviceNamespace.ifPresent(serviceNamespace -> logQl.append(LABEL_SERVICE_NAMESPACE).append("=\"").append(serviceNamespace).append("\", "));
        logQl.append(LABEL_SERVICE_NAME + "=\"" + serviceName + "\"}");

        logQl.append("|" +
            META_DATA_TRACE_ID + "=\"" + traceId + "\", " +
            META_DATA_CI_PIPELINE_ID + "=\"" + jobFullName + "\", " +
            META_DATA_CI_PIPELINE_RUN_NUMBER + "=" + runNumber);
        flowNodeId.ifPresent(flowNodeId -> logQl.append(", " + META_DATA_JENKINS_PIPELINE_STEP_ID + "=\"" + flowNodeId + "\""));

        logQl.append(" | keep __line__");

        RequestBuilder lokiQueryRangeRequestBuilder = RequestBuilder
            .get()
            .setUri(lokiUrl + "/loki/api/v1/query_range")
            .addParameter("query", logQl.toString())
            .addParameter("start", String.valueOf(startTimeInNanos))
            .addParameter("direction", "forward")
            .addParameter("limit", String.valueOf(MAX_LINES_PER_REQUEST));

        endTimeInNanos.ifPresent(endTimeInNanos -> lokiQueryRangeRequestBuilder.addParameter("end", String.valueOf(endTimeInNanos)));

        HttpUriRequest lokiQueryRangeRequest = lokiQueryRangeRequestBuilder.build();
        lokiCredentials.ifPresent(credentials -> {
            // preemptive authentication due to a limitation of Grafana Cloud Logs (Loki) that doesn't return
            // `WWW-Authenticate` header to trigger traditional authentication
            try {
                lokiQueryRangeRequest.addHeader(new BasicScheme().authenticate(credentials, lokiQueryRangeRequest, httpContext));
            } catch (AuthenticationException e) {
                throw new RuntimeException(e);
            }
        });
        lokiTenantId.ifPresent(tenantId -> lokiQueryRangeRequest.addHeader(new LokiTenantHeader(tenantId)));

        logger.log(Level.FINE, () -> "Request start: " + startTimeInNanos + " end: " + endTimeInNanos);
        queryCounter++;
        try (CloseableHttpResponse lokiQueryRangeResponse = httpClient.execute(lokiQueryRangeRequest, this.httpContext)) {
            if (lokiQueryRangeResponse.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Loki logs query failure: " + lokiQueryRangeResponse.getStatusLine() + " - " + EntityUtils.toString(lokiQueryRangeResponse.getEntity()));
            }
            HttpEntity entity = lokiQueryRangeResponse.getEntity();
            if (entity == null) {
                logger.log(Level.INFO, () -> "No content in response for " + logQl + " start: " + startTimeInNanos + " end: " + endTimeInNanos);
                return Collections.emptyIterator();
            }
            InputStream lokiQueryLogsResponseStream = entity.getContent();
            return loadLogLines(lokiQueryLogsResponseStream);
        }
    }

    @Nonnull
    @VisibleForTesting
    protected Iterator<String> loadLogLines(InputStream lokiQueryResponseInputStream) throws IOException {
        Iterator<String> logLineIterator = JsonPath.<List<List<String>>>read(lokiQueryResponseInputStream, "$.data.result[*].values[*]").stream().map(valueKeyPair -> {
            long timestampInNanos = Long.parseLong(valueKeyPair.get(0));
            String msg = valueKeyPair.get(1);
            if (timestampInNanos < startTimeInNanos) {
                logger.log(Level.INFO, () -> "Unordered timestamps " + timestampInNanos + " < " + startTimeInNanos
                    + " for " + jobFullName + " #" + runNumber
                    + " traceId: " + traceId + " flowNodeId: " + flowNodeId);
            } else {
                startTimeInNanos = timestampInNanos + 1; // +1 because `start` is >=
            }
            return msg;
        }).iterator();

        return new CloseableIterator<>(logLineIterator, lokiQueryResponseInputStream);
    }

    @Override
    public void skipLines(long skipLines) {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        SpanBuilder spanBuilder = tracer.spanBuilder("LokiBuildLogsLineIterator.skip")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute("skipLines", skipLines);
        Span span = spanBuilder.startSpan();
        try {
            this.lineCounter = skipLines;
            if (this.delegate == null) {
                span.setAttribute("skippedLines", -1);
            } else {
                /*
                 * Happens when invoked by:
                 * GET /job/:jobFullName/:runNumber/consoleText
                 * |- org.jenkinsci.plugins.workflow.job.WorkflowRun.doConsoleText
                 *    |- io.jenkins.plugins.opentelemetry.job.log.OverallLog.writeLogTo(long, java.io.OutputStream)
                 *       |- org.kohsuke.stapler.framework.io.LargeText.writeLogTo(long, java.io.OutputStream)
                 * GET /blue/rest/organizations/:organization/pipelines/:pipeline/branches/:branch/runs/:runNumber/log?start=0
                 *
                 * When invoked by "/job/:jobFullName/:runNumber/consoleText", it's the second call to LargeText.writeLogTo() and it's EOF
                 */
                int counter = 0;
                for (int i = 0; i < skipLines; i++) {
                    if (delegate.hasNext()) {
                        delegate.next();
                        counter++;
                    } else {
                        break;
                    }
                }
                span.setAttribute("skippedLines", counter);
            }
        } finally {
            span.end();
        }
    }

    @Override
    public boolean hasNext() {
        return getCurrentIterator().hasNext();
    }

    @Override
    public String next() {
        lineCounter++;
        return getCurrentIterator().next();
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable) {
            try {
                ((AutoCloseable) delegate).close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to close delegate for " + jobFullName + "#" + runNumber, e);
            }
        }
        try {
            this.httpClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
