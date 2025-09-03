/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.trace.Tracer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.jenkinsci.plugins.workflow.log.LogStorageFactoryDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Binds Otel Logs to Pipeline logs.
 * <p>
 * See <a href="https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/PipelineBridge.java">Pipeline Cloudwatch Logs - PipelineBridge</a>
 */
public final class OtelLogStorageFactory implements LogStorageFactory, OpenTelemetryLifecycleListener {

    private static final Logger logger = Logger.getLogger(OtelLogStorageFactory.class.getName());

    static {
        // Make sure JENKINS-52165 is enabled, or performance will be awful for remote shell steps.
        System.setProperty("org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep.USE_WATCHING", "true");
    }

    private JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @Nullable
    private OtelTraceService otelTraceService;

    private Tracer tracer;

    @DataBoundConstructor
    public OtelLogStorageFactory() {}

    /**
     * Create a LogStorage for a given FlowExecutionOwner
     * @param owner the FlowExecutionOwner
     * @return the LogStorage, null if no Opentelemetry data is found, or a BrokenLogStorage if an error occurs.
     */
    @Nullable
    @Override
    public LogStorage forBuild(@NonNull final FlowExecutionOwner owner) {
        LogStorage ret = null;
        if (!getJenkinsControllerOpenTelemetry().isLogsEnabled()) {
            logger.log(Level.FINE, () -> "OTel Logs disabled");
            return ret;
        }
        try {
            Queue.Executable exec = owner.getExecutable();
            ret = forExec(exec);
        } catch (IOException x) {
            ret = new BrokenLogStorage(x);
        }
        return ret;
    }

    /**
     * Create a LogStorage for a given Queve Executable
     * @param exec the Queue Executable
     * @return the LogStorage or null if no Opentelemetry data is found
     */
    @Nullable
    private LogStorage forExec(@NonNull Queue.Executable exec) {
        LogStorage ret = null;
        if (exec instanceof Run<?, ?> run && run.getAction(MonitoringAction.class) != null) {
            // it's a pipeline with monitoring data
            logger.log(Level.FINEST, () -> "forExec(" + run + ")");
            ret = new OtelLogStorage(run, getOtelTraceService(), getTracer());
        }
        return ret;
    }

    /**
     * Workaround dependency injection problem. @Inject doesn't work here
     */
    @NonNull
    private JenkinsControllerOpenTelemetry getJenkinsControllerOpenTelemetry() {
        if (jenkinsControllerOpenTelemetry == null) {
            jenkinsControllerOpenTelemetry = JenkinsControllerOpenTelemetry.get();
        }
        return jenkinsControllerOpenTelemetry;
    }

    /**
     * Workaround dependency injection problem. @Inject doesn't work here
     */
    @NonNull
    private OtelTraceService getOtelTraceService() {
        if (otelTraceService == null) {
            otelTraceService = OtelTraceService.get();
        }
        return otelTraceService;
    }

    @NonNull
    public Tracer getTracer() {
        if (tracer == null) {
            tracer = getJenkinsControllerOpenTelemetry().getDefaultTracer();
        }
        return tracer;
    }

    @Extension()
    @Symbol("openTelemetry")
    public static final class DescriptorImpl extends LogStorageFactoryDescriptor<OtelLogStorageFactory> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "OpenTelemetry";
        }

        @Override
        public LogStorageFactory getDefaultInstance() {
            return new OtelLogStorageFactory();
        }
    }
}
