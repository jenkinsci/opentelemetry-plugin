package io.jenkins.plugins.opentelemetry.trace;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.MustBeClosed;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOtelPlugin;
import io.jenkins.plugins.opentelemetry.trace.context.RunContextKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class OtelTraceService {

    private static Logger LOGGER = Logger.getLogger(OtelTraceService.class.getName());

    private final Multimap<RunIdentifier, Span> spansByRun = ArrayListMultimap.create();

    private Tracer tracer;

    @CheckForNull
    public Span getSpan(@Nonnull Run run) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        LOGGER.log(Level.FINER, () -> "getSpan(" + run.getFullDisplayName() + ") - stack: " + getStackOfSpans(run));
        Collection<Span> runSpans = this.spansByRun.get(runIdentifier);
        return runSpans == null ? null : Iterables.getLast(runSpans, null);
    }

    protected String getStackOfSpans(@Nonnull Run run) {
        return this.spansByRun.asMap().get(run).stream().map(s -> ((ReadableSpan) s).getName()).collect(Collectors.joining(","));
    }

    public boolean removeSpan(@Nonnull Run run, @Nonnull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        boolean spanRemoved = this.spansByRun.remove(runIdentifier, span);
        LOGGER.log(Level.FINER, () -> "removeSpan(" + run.getFullDisplayName() + ") - stack: " + getStackOfSpans(run));
        if (spanRemoved) {
            return true;
        } else {
            LOGGER.log(Level.INFO, () -> "removeSpan(" + run.getFullDisplayName() + ") - Failure to remove span : " + span + " -  stack: " + getStackOfSpans(run));
            return true;
        }
    }

    /**
     * @param run
     * @return count of remaining spans found for the given run {@link Run}
     */
    public int purgeRun(@Nonnull Run run) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        Collection<Span> remainingSpans = this.spansByRun.removeAll(runIdentifier);
        LOGGER.log(Level.FINER, () -> "purgeRun(" + run.getFullDisplayName() + "), stack: " + getStackOfSpans(run));

        // TODO shall we {@code Span#end()} all these spans?
        return remainingSpans.size();
    }

    public void dumpContext(@Nonnull Run run, String message, @Nonnull PrintStream out) {
        ReadableSpan span = (ReadableSpan) getSpan(run);
        SpanData spanData = span.toSpanData();
    }


    public void putSpan(@Nonnull Run run, @Nonnull Span span) {
        RunIdentifier runIdentifier = RunIdentifier.fromRun(run);
        this.spansByRun.put(runIdentifier, span);

        LOGGER.log(Level.FINER, () -> "putSpan(" + run.getFullDisplayName() + "," + span + ") - new stack: " + getStackOfSpans(run));

    }

    @Inject
    public void setJenkinsOtelPlugin(@Nonnull JenkinsOtelPlugin jenkinsOtelPlugin) {
        this.tracer = jenkinsOtelPlugin.getTracer();
    }

    /**
     * @param run
     * @return {@code null} if no {@link Span} has been created for the given {@link Run}
     */
    @CheckForNull
    @MustBeClosed
    public Scope setupContext(@Nonnull Run run) {
        Span span = getSpan(run);
        if (span == null) {
            return null;
        } else {
            Scope scope = span.makeCurrent();
            Context.current().with(RunContextKey.KEY, run);
            return scope;
        }
    }

    public Tracer getTracer() {
        return tracer;
    }

    @Immutable
    public static class RunIdentifier implements Serializable, Comparable<RunIdentifier> {
        final String jobName;
        final int runNumber;

        static RunIdentifier fromRun(@Nonnull Run run) {
            return new RunIdentifier(run.getParent().getFullName(), run.getNumber());
        }

        public RunIdentifier(@Nonnull String jobName, @Nonnull int runNumber) {
            this.jobName = jobName;
            this.runNumber = runNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RunIdentifier that = (RunIdentifier) o;
            return runNumber == that.runNumber && jobName.equals(that.jobName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jobName, runNumber);
        }

        @Override
        public String toString() {
            return "RunIdentifier{" +
                    "jobName='" + jobName + '\'' +
                    ", runNumber=" + runNumber +
                    '}';
        }

        public String getJobName() {
            return jobName;
        }

        public int getRunNumber() {
            return runNumber;
        }

        @Override
        public int compareTo(RunIdentifier o) {
            return ComparisonChain.start().compare(this.jobName, o.jobName).compare(this.runNumber, o.runNumber).result();
        }
    }

}
