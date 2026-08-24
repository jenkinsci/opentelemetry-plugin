# Troubleshooting

This guide covers common issues when setting up the Jenkins OpenTelemetry Plugin.

---

## OTLP endpoint connection failures

**Symptom:** No traces or metrics appear in your backend. The Jenkins system log shows:

io.opentelemetry.exporter.internal.grpc.GrpcExporter - Failed to export spans.


**Checklist:**

1. Check the logs first — Jenkins system log, OpenTelemetry Collector log,
   and your observability backend log. The error message will tell you exactly
   where the failure is occurring.

2. Verify the OTLP endpoint URL is correct.
   - Default gRPC port: `4317`
   - Default HTTP/Protobuf port: `4318`
   - Example: `http://my-otel-collector:4317`

3. Confirm the OpenTelemetry Collector is running:
   bash
   systemctl status otelcol
   # or
   docker ps | grep otel

4. Check that firewall rules allow traffic on the configured port.

5. To confirm the plugin is sending data at all, temporarily switch the
   exporter to `logging` in **Manage Jenkins → Configure System → OpenTelemetry**.
   This writes telemetry to stdout.

6. If using TLS, verify the certificate is trusted by Jenkins' JVM:
   bash
   keytool -importcert -alias otel-collector \
     -file /path/to/cert.pem \
     -keystore $JAVA_HOME/lib/security/cacerts


---

## Authentication errors

**Symptom:** The Jenkins log shows `UNAUTHENTICATED` or `PERMISSION_DENIED`.

**Checklist:**

1. Check the Jenkins system log first for the full error message — it will
   show whether the failure is a token issue or a network/TLS issue.

2. Verify the Bearer token is set in **Manage Jenkins → Configure System
   → OpenTelemetry → Authentication**.

3. Ensure the credential has no leading or trailing whitespace.

4. Confirm the token has not expired. Rotate it in your backend and update
   the Jenkins credential.

5. If using the environment variable approach, verify the format:

   OTEL_EXPORTER_OTLP_HEADERS=Authorization=Bearer YOUR_TOKEN


---

## Missing ci.pipeline.* metrics

**Symptom:** `jenkins.*` metrics (such as `jenkins.job.duration`) arrive
correctly in your backend, but `ci.pipeline.*` metrics are absent. This
happens because `ci.pipeline.*` metrics use OpenTelemetry resource attributes
that some backends require explicit configuration to preserve.

**Checklist:**

1. Ensure your backend supports OTLP metrics. Prometheus requires the
   OpenTelemetry Collector with a Prometheus exporter configured.

2. Enable `resource_to_telemetry_conversion` on the Collector so that
   resource attributes are converted to metric labels and not dropped:
   yaml
   exporters:
     prometheus:
       endpoint: "0.0.0.0:1234"
       resource_to_telemetry_conversion:
         enabled: true


3. Verify at least one Pipeline build has completed since the plugin
   was installed.

Related: [Issue #930](https://github.com/jenkinsci/opentelemetry-plugin/issues/930)

---

## Enabling debug logs

1. Go to **Manage Jenkins → System Log → Add new log recorder**.
2. Set logger name: `io.jenkins.plugins.opentelemetry`
3. Set level: `FINE`

This shows detailed span creation, metric recording, and exporter activity.

---

## Still stuck?

- Search [GitHub Issues](https://github.com/jenkinsci/opentelemetry-plugin/issues)
- Open a new issue with your Jenkins version, plugin version, and log output
- Ask on [Jenkins community Discourse](https://community.jenkins.io)