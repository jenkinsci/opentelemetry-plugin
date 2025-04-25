/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;

import com.google.common.annotations.VisibleForTesting;
import com.jayway.jsonpath.JsonPath;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.plugins.opentelemetry.job.log.LogLine;
import io.jenkins.plugins.opentelemetry.job.log.util.CloseableIterator;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIterator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;

/*
 * HttpClient can't do preemptive auth and Loki doesn't return `WWW-Authenticate` header when authentication is
 * needed so use Apache HTTP Client instead.
 */
public class LokiBuildLogsLineIterator implements LogLineIterator<Long>, AutoCloseable {

    protected final static Logger logger = Logger.getLogger(LokiBuildLogsLineIterator.class.getName());
    public final static int MAX_QUERIES = 100;

    protected final LokiGetJenkinsBuildLogsQueryParameters lokiQueryParameters;

    final String lokiUrl;
    final Optional<Credentials> lokiCredentials;
    final Optional<String> lokiTenantId;

    final CloseableHttpClient httpClient;
    final HttpContext httpContext;


    @VisibleForTesting
    int queryCounter;

    final Tracer tracer;

    Iterator<LogLine<Long>> delegate;
    boolean endOfStream;

    public LokiBuildLogsLineIterator(

        @NonNull LokiGetJenkinsBuildLogsQueryParameters lokiQueryParameters,

        @NonNull CloseableHttpClient httpClient,
        @NonNull HttpContext httpContext,

        @NonNull String lokiUrl,
        @NonNull Optional<Credentials> lokiCredentials,
        @NonNull Optional<String> lokiTenantId,
        @NonNull Tracer tracer) {

        this.lokiQueryParameters = lokiQueryParameters;

        this.lokiUrl = lokiUrl;
        this.lokiCredentials = lokiCredentials;
        this.lokiTenantId = lokiTenantId;

        this.httpClient = httpClient;
        this.httpContext = httpContext;

        this.tracer = tracer;
    }

    @NonNull
    Iterator<LogLine<Long>> getCurrentIterator() {
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
            if (!delegate.hasNext()) {
                endOfStream = true;
            }
            return delegate;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Iterator<LogLine<Long>> loadNextLogLines() throws IOException {
        if (queryCounter > MAX_QUERIES) {
            logger.log(Level.INFO, () -> "Circuit breaker: "
                + queryCounter + " queries for " + this.lokiQueryParameters);
            return Collections.emptyIterator();
        }

        Span loadNextLogLinesSpan = tracer.spanBuilder("LokiBuildLogsLineIterator.loadNextLogLines")
            .setAllAttributes(this.lokiQueryParameters.toAttributes())
            .startSpan();
        try (Scope loadNextLogLinesScope = loadNextLogLinesSpan.makeCurrent()) {

            ClassicHttpRequest lokiQueryRangeRequest = this.lokiQueryParameters.toHttpRequest(lokiUrl);
            lokiCredentials.ifPresent(credentials -> {
                // preemptive authentication due to a limitation of Grafana Cloud Logs (Loki) that doesn't return
                // `WWW-Authenticate` header to trigger traditional authentication
                try {
                    BasicScheme basicScheme = new BasicScheme();
                    basicScheme.initPreemptive(lokiCredentials.get());
                    lokiQueryRangeRequest.addHeader("Authentication", new BasicScheme().generateAuthResponse(null, lokiQueryRangeRequest, httpContext));
                } catch (AuthenticationException e) {
                    throw new RuntimeException(e);
                }
            });
            lokiTenantId.ifPresent(tenantId -> lokiQueryRangeRequest.addHeader(new LokiTenantHeader(tenantId)));

            queryCounter++;
            return httpClient.execute(lokiQueryRangeRequest, this.httpContext, new HttpClientResponseHandler<Iterator<LogLine<Long>>>() {
                @Override
                public Iterator<LogLine<Long>> handleResponse(ClassicHttpResponse lokiQueryRangeResponse) throws IOException {
                    try {
                        if (lokiQueryRangeResponse.getCode() != 200) {
                            throw new IOException("Loki logs query failure: " +
                                lokiQueryRangeResponse.getReasonPhrase() + " - " +
                                EntityUtils.toString(lokiQueryRangeResponse.getEntity()));
                        }
                        HttpEntity entity = lokiQueryRangeResponse.getEntity();
                        if (entity == null) {
                            logger.log(Level.INFO, "No content in response for " + lokiQueryParameters);
                            return Collections.emptyIterator();
                        }
                        InputStream lokiQueryLogsResponseStream = entity.getContent();
                        return loadLogLines(lokiQueryLogsResponseStream);
                    } catch (ParseException e) {
                        throw new IOException(e);
                    }
                }
            });
        }
    }

    @Nonnull
    @VisibleForTesting
    protected Iterator<LogLine<Long>> loadLogLines(InputStream lokiQueryResponseInputStream) throws IOException {
        Iterator<LogLine<Long>> logLineIterator = JsonPath.<List<List<String>>>read(lokiQueryResponseInputStream, "$.data.result[*].values[*]").stream().map(valueKeyPair -> {
            long timestampInNanos = Long.parseLong(valueKeyPair.get(0));
            String msg = valueKeyPair.get(1);
            if (timestampInNanos < lokiQueryParameters.getStartTimeInNanos()) {
                logger.log(Level.INFO, () -> "Unordered timestamps " + timestampInNanos + " < " + lokiQueryParameters.getStartTimeInNanos()
                    + " for " + lokiQueryParameters);
            } else {
                lokiQueryParameters.setStartTimeInNanos(timestampInNanos + 1); // +1 because `start` is >=
            }
            return new LogLine<>(timestampInNanos, msg);
        }).iterator();

        return new CloseableIterator<>(logLineIterator, lokiQueryResponseInputStream);
    }

    @Override
    public void skipLines(Long lastLogTimestampInNanos) {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("LokiBuildLogsLineIterator.skip")
            .setAllAttributes(this.lokiQueryParameters.toAttributes())
            .setAttribute("lastLogTimestampInNanos", lastLogTimestampInNanos)
            .startSpan();
        long newStartTimeInNanos = lastLogTimestampInNanos + 1;
        try {
            if (this.delegate == null) {
                span.setAttribute("skippedLines", -1);
                lokiQueryParameters.setStartTimeInNanos(newStartTimeInNanos);
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
                span.setAttribute("skippedLines", -1);
                lokiQueryParameters.setStartTimeInNanos(newStartTimeInNanos);
                this.delegate = null; // TODO optimize to skip lines in the current delegate
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
    public LogLine<Long> next() {
        return getCurrentIterator().next();
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable) {
            try {
                ((AutoCloseable) delegate).close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to close delegate for " + lokiQueryParameters, e);
            }
        }
        try {
            this.httpClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
