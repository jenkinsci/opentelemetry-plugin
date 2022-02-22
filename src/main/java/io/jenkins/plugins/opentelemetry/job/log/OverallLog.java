/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import hudson.Main;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.apache.commons.io.output.CountingOutputStream;
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
    final static String ATTRIBUTE_LENGTH = "buffer.length";
    private final static Logger logger = Logger.getLogger(OverallLog.class.getName());
    private final FlowExecutionOwner.Executable context;
    private final ByteBuffer byteBuffer;
    private final transient Tracer tracer;
    private final LogsViewHeader logsViewHeader;

    public OverallLog(ByteBuffer memory, LogsViewHeader logsViewHeader, Charset charset, boolean completed, FlowExecutionOwner.Executable context, Tracer tracer) {
        super(memory, charset, completed, context);
        this.byteBuffer = memory;
        this.logsViewHeader = logsViewHeader;
        this.context = context;
        this.tracer = tracer;
    }

    @Override
    public void doProgressiveHtml(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Span span = tracer.spanBuilder("OverallLog.doProgressiveHtml")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(ATTRIBUTE_LENGTH, this.byteBuffer.length());
            super.doProgressiveHtml(req, rsp);
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    public void doProgressiveText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        logger.log(Level.FINE, () -> "doProgressiveText(buffer.length" + this.byteBuffer.length() + ")");
        Span span = tracer.spanBuilder("OverallLog.doProgressiveText")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(ATTRIBUTE_LENGTH, this.byteBuffer.length());
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
        logger.log(Level.FINE, () -> "writeLogTo(start: " + start + ", buffer.length: " + this.byteBuffer.length() + ")");
        Span span = tracer.spanBuilder("OverallLog.writeLogTo")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(ATTRIBUTE_LENGTH, this.byteBuffer.length());
            return super.writeLogTo(start, w);
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Called by `/job/:jobFullName/:runNumber/consoleText`
     * FIXME add link to logs visualization screen.
     */
    @Override
    public long writeLogTo(long start, OutputStream out) throws IOException {
        logger.log(Level.FINE, () -> "writeLogTo(start: " + start + ", buffer.length: " + this.byteBuffer.length() + ")");
        Span span = tracer.spanBuilder("OverallLog.writeLogTo")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(ATTRIBUTE_LENGTH, this.byteBuffer.length());
            return super.writeLogTo(start, out);
        }
    }

    @Override
    public long writeRawLogTo(long start, OutputStream out) throws IOException {
        logger.log(Level.FINE, () -> "writeRawLogTo(start: " + start + ", buffer.length: " + this.byteBuffer.length() + ")");
        Span span = tracer.spanBuilder("OverallLog.writeRawLogTo")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(ATTRIBUTE_LENGTH, this.byteBuffer.length());
            return super.writeRawLogTo(start, out);
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
        logger.log(Level.FINE, () -> "writeHtmlTo(start: " + start + ", buffer.length: " + this.byteBuffer.length() + ")");

        Span span = tracer.spanBuilder("OverallLog.writeHtmlTo")
            .startSpan();
        long length = 0;
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(ATTRIBUTE_LENGTH, this.byteBuffer.length());
            // HEADER
            if (start == 0 && !Main.isUnitTest) { // would mess up unit tests
                length += logsViewHeader.writeHeader(w, context, charset);
                w.write("\n\n");  // TODO increment length
            }
            // LOG LINES
            long logLinesLengthInBytes = super.writeHtmlTo(start, w);
            length += logLinesLengthInBytes;

            // FOOTER
            if (logLinesLengthInBytes >0) { // some log lines have been emitted, append a footer
                if (!isComplete()) {
                    w.write("...");  // TODO increment length
                }
                w.write("\n\n"); // TODO increment length

                length += logsViewHeader.writeHeader(w, context, charset);
            }
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
        logger.log(Level.FINE, () -> "readAll(" + ", buffer.length: " + this.byteBuffer.length() + ")");
        Span span = tracer.spanBuilder("OverallLog.readAll")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(ATTRIBUTE_LENGTH, this.byteBuffer.length());
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
        logger.log(Level.FINE, () -> "doProgressText(" + ", buffer.length: " + this.byteBuffer.length() + ")");
        Span span = tracer.spanBuilder("OverallLog.doProgressText")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(ATTRIBUTE_LENGTH, this.byteBuffer.length());
            super.doProgressText(req, rsp);
        } catch (IOException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
