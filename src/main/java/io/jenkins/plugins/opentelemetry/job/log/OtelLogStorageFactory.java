/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.opentelemetry.api.incubator.events.EventLogger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Binds Otel Logs to Pipeline logs.
 * <p>
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/pipeline-cloudwatch-logs-0.2/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/PipelineBridge.java
 */
@Extension
public final class OtelLogStorageFactory implements LogStorageFactory, OtelComponent {

    private final static Logger logger = Logger.getLogger(OtelLogStorageFactory.class.getName());

    static {
        // Make sure JENKINS-52165 is enabled, or performance will be awful for remote shell steps.
        System.setProperty("org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep.USE_WATCHING", "true");
    }

    @Nullable
    private OpenTelemetrySdkProvider openTelemetrySdkProvider;

    @Nullable
    private OtelTraceService otelTraceService;

    private Tracer tracer;

    static OtelLogStorageFactory get() {
        return ExtensionList.lookupSingleton(OtelLogStorageFactory.class);
    }

    @Nullable
    @Override
    public LogStorage forBuild(@NonNull final FlowExecutionOwner owner) {
        if (!getOpenTelemetrySdkProvider().isOtelLogsEnabled()) {
            logger.log(Level.FINE, () -> "OTel Logs disabled");
            return null;
        }

        try {
            Queue.Executable exec = owner.getExecutable();
            if (exec instanceof Run) {
                Run<?, ?> run = (Run<?, ?>) exec;

                logger.log(Level.FINEST, () -> "forBuild(" + run + ")");

                MonitoringAction monitoringAction = run.getAction(MonitoringAction.class);
                
                if(monitoringAction != null){
                    return new OtelLogStorage(run, getOtelTraceService(), tracer, monitoringAction);
                } else{
                    logger.log(Level.FINE, () -> "No MonitoringAction found for " + run);
                    return null;
                }
            } else {
                return null;
            }
        } catch (IOException x) {
            return new BrokenLogStorage(x);
        }
    }
    /**
     * Workaround dependency injection problem. @Inject doesn't work here
     */
    @NonNull
    private OpenTelemetrySdkProvider getOpenTelemetrySdkProvider() {
        if (openTelemetrySdkProvider == null) {
            openTelemetrySdkProvider = OpenTelemetrySdkProvider.get();
        }
        return openTelemetrySdkProvider;
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

    @Override
    public void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventLogger eventLogger, Tracer tracer, ConfigProperties configProperties) {
        this.tracer = tracer;
    }
}