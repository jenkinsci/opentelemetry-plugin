package io.jenkins.plugins.opentelemetry.job;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey.*;

/**
 * Inject OpenTelemetry environment variables in shell steps: {@code TRACEPARENT}, {@code OTEL_EXPORTER_OTLP_ENDPOINT}...
 *
 * @see org.jenkinsci.plugins.workflow.steps.StepEnvironmentContributor
 * @see hudson.model.EnvironmentContributor
 */
@Extension
public class OtelEnvironmentContributorService {

    public static final String SPAN_ID = "SPAN_ID";
    public static final String TRACE_ID = "TRACE_ID";

    private final List<ConfigurationKey> exportedConfigKeys = List.of(
        OTEL_EXPORTER_OTLP_CERTIFICATE,
        OTEL_EXPORTER_OTLP_ENDPOINT,
        OTEL_EXPORTER_OTLP_HEADERS,
        OTEL_EXPORTER_OTLP_INSECURE,
        OTEL_EXPORTER_OTLP_PROTOCOL,
        OTEL_EXPORTER_OTLP_TIMEOUT,
        OTEL_IMR_EXPORT_INTERVAL,
        OTEL_LOGS_EXPORTER,
        OTEL_METRICS_EXPORTER,
        OTEL_TRACES_EXPORTER
    );

    private ReconfigurableOpenTelemetry reconfigurableOpenTelemetry;

    public void addEnvironmentVariables(@NonNull Run<?, ?> run, @NonNull EnvVars envs, @NonNull Span span) {
        String spanId = span.getSpanContext().getSpanId();
        String traceId = span.getSpanContext().getTraceId();
        envs.put(TRACE_ID, traceId);
        envs.put(SPAN_ID, spanId);
        try (Scope ignored = span.makeCurrent()) {
            TextMapSetter<EnvVars> setter = (carrier, key, value) -> carrier.put(key.toUpperCase(), value);
            W3CTraceContextPropagator.getInstance().inject(Context.current(), envs, setter);
        }
        Baggage baggage = Baggage.builder()
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID.getKey(), run.getParent().getFullName())
            .put(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER.getKey(), String.valueOf(run.getNumber()))
            .build();
        try (Scope ignored = baggage.makeCurrent()) {
            TextMapSetter<EnvVars> setter = (carrier, key, value) -> carrier.put(key.toUpperCase(), value);
            W3CBaggagePropagator.getInstance().inject(Context.current(), envs, setter);
        }

        Optional.ofNullable(run.getAction(MonitoringAction.class))
            .ifPresent(monitoringAction -> {
                // Add visualization link as environment variables to provide visualization links in notifications (to GitHub, slack messages...)
                monitoringAction.getLinks().stream()
                    .filter(link -> link.getEnvironmentVariableName() != null)
                    .forEach(link -> envs.put(link.getEnvironmentVariableName(), link.getUrl()));
            });

        ConfigProperties config = reconfigurableOpenTelemetry.getConfig();
        boolean exportOTelConfigAsEnvVar = config.getBoolean(OTEL_INSTRUMENTATION_JENKINS_EXPORT_OTEL_CONFIG_AS_ENV_VARS.asProperty(), true);
        if (exportOTelConfigAsEnvVar) {
            for (ConfigurationKey configKey : exportedConfigKeys) {
                Optional.ofNullable(config.getString(configKey.asProperty()))
                    .ifPresent(configValue -> envs.put(configKey.asEnvVar(), configValue));
            }
        }
    }

    @Inject
    public void setReconfigurableOpenTelemetry(ReconfigurableOpenTelemetry reconfigurableOpenTelemetry) {
        this.reconfigurableOpenTelemetry = reconfigurableOpenTelemetry;
    }
}
