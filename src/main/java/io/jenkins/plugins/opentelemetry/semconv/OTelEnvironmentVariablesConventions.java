/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.semconv;

/**
 * Java constants for the
 * [OpenTelemetry Environment variables conventions](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/exporter.md)
 * [OpenTelemetry Java SDK Autoconfigure](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure)
 */
public class OTelEnvironmentVariablesConventions {

    public static final String OTEL_EXPORTER_OTLP_CERTIFICATE = "OTEL_EXPORTER_OTLP_CERTIFICATE";
    public static final String OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";
    public static final String OTEL_EXPORTER_OTLP_INSECURE = "OTEL_EXPORTER_OTLP_INSECURE";
    public static final String OTEL_EXPORTER_OTLP_TIMEOUT = "OTEL_EXPORTER_OTLP_TIMEOUT";
    public static final String OTEL_TRACES_EXPORTER = "OTEL_TRACES_EXPORTER";

    public static final String SPAN_ID = "SPAN_ID";
    public static final String TRACE_ID = "TRACE_ID";

}
