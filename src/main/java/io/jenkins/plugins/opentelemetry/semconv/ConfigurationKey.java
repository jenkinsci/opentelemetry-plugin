package io.jenkins.plugins.opentelemetry.semconv;

import java.util.Objects;

/**
 * Configuration key for the OpenTelemetry SDK {@link io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties}.
 */
public final class ConfigurationKey {

    public static final ConfigurationKey OTEL_EXPORTER_JAEGER_ENDPOINT =
            new ConfigurationKey("otel.exporter.jaeger.endpoint");
    public static final ConfigurationKey OTEL_EXPORTER_OTLP_CERTIFICATE =
            new ConfigurationKey("otel.exporter.otlp.certificate");
    public static final ConfigurationKey OTEL_EXPORTER_OTLP_ENDPOINT =
            new ConfigurationKey("otel.exporter.otlp.endpoint");
    public static final ConfigurationKey OTEL_EXPORTER_OTLP_HEADERS =
            new ConfigurationKey("otel.exporter.otlp.headers");
    public static final ConfigurationKey OTEL_EXPORTER_OTLP_INSECURE =
            new ConfigurationKey("otel.exporter.otlp.insecure");
    public static final ConfigurationKey OTEL_EXPORTER_OTLP_METRICS_ENDPOINT =
            new ConfigurationKey("otel.exporter.otlp.metrics.endpoint");
    public static final ConfigurationKey OTEL_EXPORTER_OTLP_PROTOCOL =
            new ConfigurationKey("otel.exporter.otlp.protocol");
    public static final ConfigurationKey OTEL_EXPORTER_OTLP_TIMEOUT =
            new ConfigurationKey("otel.exporter.otlp.timeout");
    public static final ConfigurationKey OTEL_EXPORTER_OTLP_TRACES_ENDPOINT =
            new ConfigurationKey("otel.exporter.otlp.traces.endpoint");
    public static final ConfigurationKey OTEL_EXPORTER_PROMETHEUS_PORT =
            new ConfigurationKey("otel.exporter.prometheus.port");

    public static final ConfigurationKey OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS =
            new ConfigurationKey("otel.java.disabled.resource.providers");

    public static final ConfigurationKey OTEL_LOGS_EXPORTER = new ConfigurationKey("otel.logs.exporter");
    public static final ConfigurationKey OTEL_LOGS_MIRROR_TO_DISK = new ConfigurationKey("otel.logs.mirror_to_disk");

    public static final ConfigurationKey OTEL_METRIC_EXPORT_INTERVAL =
            new ConfigurationKey("otel.metric.export.interval");
    public static final ConfigurationKey OTEL_METRICS_EXPORTER = new ConfigurationKey("otel.metrics.exporter");

    public static final ConfigurationKey OTEL_RESOURCE_ATTRIBUTES = new ConfigurationKey("otel.resource.attributes");

    public static final ConfigurationKey OTEL_SERVICE_NAME = new ConfigurationKey("otel.service.name");

    public static final ConfigurationKey OTEL_TRACES_EXPORTER = new ConfigurationKey("otel.traces.exporter");

    public static final ConfigurationKey OTEL_INSTRUMENTATION_JENKINS_WEB_ENABLED =
            new ConfigurationKey("otel.instrumentation.jenkins.web.enabled");
    public static final ConfigurationKey OTEL_INSTRUMENTATION_JENKINS_REMOTE_SPAN_ENABLED =
            new ConfigurationKey("otel.instrumentation.jenkins.remote.span.enabled");
    public static final ConfigurationKey OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_ALLOW_LIST =
            new ConfigurationKey("otel.instrumentation.jenkins.run.metric.duration.allow_list");
    public static final ConfigurationKey OTEL_INSTRUMENTATION_JENKINS_RUN_DURATION_DENY_LIST =
            new ConfigurationKey("otel.instrumentation.jenkins.run.metric.duration.deny_list");
    /**
     * Instrument Jenkins Remoting from the Jenkins controller to Jenkins build agents
     */
    public static final ConfigurationKey OTEL_INSTRUMENTATION_JENKINS_REMOTING_ENABLED =
            new ConfigurationKey("otel.instrumentation.jenkins.remoting.enabled");
    /**
     * Instrument Jenkins build agents
     */
    public static final ConfigurationKey OTEL_INSTRUMENTATION_JENKINS_AGENTS_ENABLED =
            new ConfigurationKey("otel.instrumentation.jenkins.agent.enabled");

    public static final ConfigurationKey OTEL_INSTRUMENTATION_JENKINS_EXPORT_OTEL_CONFIG_AS_ENV_VARS =
            new ConfigurationKey("otel.instrumentation.jenkins.export_otel_config_as_env_vars");

    /**
     * <a href="https://opentelemetry.io/docs/zero-code/java/agent/instrumentation/http/#capturing-servlet-request-parameters">HTTP instrumentation configuration / Capturing HTTP request params</a>
     */
    public static final ConfigurationKey OTEL_INSTRUMENTATION_SERVLET_CAPTURE_REQUEST_PARAMETERS =
            new ConfigurationKey("otel.instrumentation.servlet.experimental.capture-request-parameters");

    private final String environmentVariableName;
    private final String propertyName;

    /**
     * @param lowerCaseName `.` separated lowercase name
     */
    private ConfigurationKey(String lowerCaseName) {
        for (char c : lowerCaseName.toCharArray()) {
            if (Character.isAlphabetic(c) && !Character.isLowerCase(c)) {
                throw new IllegalArgumentException("Invalid uppercase char in configuration key: " + lowerCaseName);
            }
        }
        this.propertyName = lowerCaseName;
        this.environmentVariableName = lowerCaseName.replace('.', '_').toUpperCase();
    }

    /**
     * Environment variable name: `_` separated uppercase name
     */
    public String asEnvVar() {
        return environmentVariableName;
    }

    /**
     * Property name: `.` separated lowercase name
     */
    public String asProperty() {
        return propertyName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ConfigurationKey that = (ConfigurationKey) o;
        return Objects.equals(environmentVariableName, that.environmentVariableName)
                && Objects.equals(propertyName, that.propertyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(environmentVariableName, propertyName);
    }

    @Override
    public String toString() {
        return "ConfigurationParameter{" + propertyName + '}';
    }
}
