package io.jenkins.plugins.opentelemetry.job;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.semconv.OTelEnvironmentVariablesConventions;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inject OpenTelemetry environment variables in shell steps: {@code TRACEPARENT}, {@code OTEL_EXPORTER_OTLP_ENDPOINT}...
 *
 * @see org.jenkinsci.plugins.workflow.steps.StepEnvironmentContributor
 * @see hudson.model.EnvironmentContributor
 */
@Extension
public class OtelEnvironmentContributorService {

    private final static Logger LOGGER = Logger.getLogger(OtelEnvironmentContributorService.class.getName());

    private JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration;

    public void addEnvironmentVariables(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull Span span) {
        String spanId = span.getSpanContext().getSpanId();
        String traceId = span.getSpanContext().getTraceId();
        try (Scope ignored = span.makeCurrent()) {
            envs.putIfAbsent(OTelEnvironmentVariablesConventions.TRACE_ID, traceId);
            envs.put(OTelEnvironmentVariablesConventions.SPAN_ID, spanId);
            TextMapSetter<EnvVars> setter = (carrier, key, value) -> carrier.put(key.toUpperCase(), value);
            W3CTraceContextPropagator.getInstance().inject(Context.current(), envs, setter);
        }

        MonitoringAction action = new MonitoringAction(traceId, spanId);
        action.onAttached(run);
        for (MonitoringAction.ObservabilityBackendLink link : action.getLinks()) {
            // Default backend link got an empty environment variable.
            if (link.getEnvironmentVariableName() != null) {
                envs.put(link.getEnvironmentVariableName(), link.getUrl());
            }
        }

        if (this.jenkinsOpenTelemetryPluginConfiguration.isExportOtelConfigurationAsEnvironmentVariables()) {
            Map<String, String> otelConfiguration = jenkinsOpenTelemetryPluginConfiguration.getOtelConfigurationAsEnvironmentVariables();
            for (Map.Entry<String, String> otelEnvironmentVariable : otelConfiguration.entrySet()) {
                String envVarValue = otelEnvironmentVariable.getValue();
                String envVarName = otelEnvironmentVariable.getKey();
                if (envVarValue == null) {
                    LOGGER.log(Level.FINE, () -> "No value found for environment variable '" + envVarName + "'");
                } else {
                    String previousValue = envs.put(envVarName, envVarValue);
                    if (previousValue != null) {
                        LOGGER.log(Level.FINE, () -> "Overwrite environment variable '" + envVarName + "'");
                    }
                }
            }
        } else {
            // skip
        }
    }

    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration) {
        this.jenkinsOpenTelemetryPluginConfiguration = jenkinsOpenTelemetryPluginConfiguration;
    }
}
