/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.Main;
import hudson.console.AnnotatedLargeText;
import io.jenkins.plugins.opentelemetry.job.log.util.StreamingInputStream;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OverallLog extends AnnotatedLargeText<FlowExecutionOwner.Executable> {
    private final static Logger logger = Logger.getLogger(OverallLog.class.getName());
    private final FlowExecutionOwner.Executable context;
    private final transient Tracer tracer;
    private final LogsViewHeader logsViewHeader;

    public OverallLog(ByteBuffer memory, LogsViewHeader logsViewHeader, Charset charset, boolean completed, FlowExecutionOwner.Executable context, Tracer tracer) {
        super(memory, charset, completed, context);
        this.logsViewHeader = logsViewHeader;
        this.context = context;
        this.tracer = tracer;
    }

    /**
     * Invoked by `/job/:jobFullName/:runNumber/logText/progressiveHtml
     */
    @Override
    public void doProgressiveHtml(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("OverallLog.doProgressiveHtml")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            String start = req.getParameter("start");
            if (start != null && !start.isEmpty()) {
                span.setAttribute("start", start);
            }
            super.doProgressiveHtml(req, rsp);
            String xTextSize = rsp.getHeader("X-Text-Size");
            if (xTextSize != null) {
                span.setAttribute("response.textSize", Long.parseLong(xTextSize));
            }
            String xMoreData = rsp.getHeader("X-More-Data");
            if (xMoreData != null) {
                span.setAttribute("response.moreData", Boolean.parseBoolean(xMoreData));
            }
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    public void doProgressiveText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("OverallLog.doProgressiveText")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            String start = req.getParameter("start");
            if (start != null && !start.isEmpty()) {
                span.setAttribute("start", start);
            }
            super.doProgressiveText(req, rsp);
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    public long writeLogTo(long start, Writer w) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("OverallLog.writeLogTo")
            .setAttribute("start", start)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            long length = super.writeLogTo(start, w);
            span.setAttribute("response.length", length);
            return length;
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Called by `/job/:jobFullName/:runNumber/consoleText` or
     * `/blue/rest/organizations/:organization/pipelines/:pipeline/branches/:branch/runs/:runNumber/log?start=0`
     * with `complete=true`
     */
    @Override
    public long writeLogTo(long start, OutputStream out) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("OverallLog.writeLogTo")
            .setAttribute("start", start)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            long length = super.writeLogTo(start, out);
            span.setAttribute("response.length", length);
            return length;
        } finally {
            span.end();
        }
    }

    /**
     * Invoked by:
     * * /job/:jobFullName/:runNumber/console
     * * {@link org.jenkinsci.plugins.workflow.job.WorkflowRun#getLogInputStream()}
     */
    @Override
    public long writeRawLogTo(long start, OutputStream out) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("OverallLog.writeRawLogTo")
            .setAttribute("start", start)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            long length = super.writeRawLogTo(start, out);
            span.setAttribute("response.length", length);
            return length;
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Inspired by io.jenkins.plugins.pipeline_cloudwatch_logs.CloudWatchRetriever.OverallLog#writeHtmlTo
     */
    @Override
    public long writeHtmlTo(long start, Writer w) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");

        Span span = tracer.spanBuilder("OverallLog.writeHtmlTo")
            .setAttribute("start", start)
            .startSpan();
        long length = 0;
        try (Scope scope = span.makeCurrent()) {
            // HEADER
            if (start == 0 && !Main.isUnitTest) { // would mess up unit tests
                length += logsViewHeader.writeHeader(w, context, charset);
                w.write("\n\n");  // TODO increment length
            }
            // LOG LINES
            long logLinesLengthInBytes = super.writeHtmlTo(start, w);
            span.setAttribute("response.length", logLinesLengthInBytes);

            length += logLinesLengthInBytes;

            return length;
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    public Reader readAll() throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("OverallLog.readAll")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return super.readAll();
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    public void doProgressText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("OverallLog.doProgressText")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            String start = req.getParameter("start");
            if (start != null && !start.isEmpty()) {
                span.setAttribute("request.start", start);
            }
            try {
                StreamingInputStream.setProgressiveStreaming();
                super.doProgressText(req, rsp);
            } finally {
                StreamingInputStream.unsetProgressiveStreaming();
            }
            String xTextSize = rsp.getHeader("X-Text-Size");
            if (xTextSize != null) {
                span.setAttribute("response.textSize", Long.parseLong(xTextSize));
            }
            String xMoreData = rsp.getHeader("X-More-Data");
            if (xMoreData != null) {
                span.setAttribute("response.moreData", Boolean.parseBoolean(xMoreData));
            }
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    protected Writer createWriter(StaplerRequest req, StaplerResponse rsp, long size) throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("OverallLog.createWriter")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return super.createWriter(req, rsp, size);
        } finally {
            span.end();
        }
    }

    @Override
    public void markAsComplete() {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        Span span = tracer.spanBuilder("OverallLog.markAsComplete")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            super.markAsComplete();
        } finally {
            span.end();
        }
    }
}
