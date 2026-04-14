# Grafana dashboard setup

This guide explains how to visualise Jenkins metrics in Grafana using the
Prometheus + OpenTelemetry Collector stack. For Elastic Kibana dashboards,
see [DASHBOARDS.md](DASHBOARDS.md).

## Overview

```
Jenkins controller
      │  OTLP/gRPC (port 4317)
      ▼
OpenTelemetry Collector
      │  Prometheus exporter (port 8889)
      ▼
Prometheus  ◄──── scrapes /metrics
      │
      ▼
Grafana  ◄──── queries Prometheus datasource
```

## Prerequisites

- Jenkins OpenTelemetry plugin installed and configured with an OTLP endpoint
- OpenTelemetry Collector running and reachable from Jenkins
- Prometheus running and reachable from the Collector
- Grafana running with a Prometheus datasource configured

---

## Step 1 - Configure the OpenTelemetry Collector

The Collector must have a `metrics` pipeline with a Prometheus exporter.
The `resource_to_telemetry_conversion` option is required - without it,
OpenTelemetry resource attributes (like `service.name`) are not converted
to Prometheus labels, so panels cannot filter by Jenkins instance.

```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317

processors:
  batch:

exporters:
  prometheus:
    endpoint: 0.0.0.0:8889
    resource_to_telemetry_conversion:
      enabled: true   # converts resource attributes &rArr; Prometheus labels

service:
  pipelines:
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus]
    traces:             # keep if you also export traces to a backend
      receivers: [otlp]
      processors: [batch]
      exporters: []     # add your trace backend exporter here
```

> ℹ️ Prometheus only supports the metrics signal. Traces and logs require
> a separate backend (Jaeger, Elastic, Loki, etc.).

---

## Step 2 - Configure Prometheus to scrape the Collector

Add a scrape job to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: jenkins-otel
    static_configs:
      - targets: ['otel-collector:8889']   # host:port of the Collector
    scrape_interval: 15s
```

Verify metrics are arriving after a build:

```bash
curl http://otel-collector:8889/metrics | grep ci_pipeline
```

You should see metrics like `ci_pipeline_run_duration_milliseconds_count`.

---

## Step 3 - Import the Grafana dashboard

A ready-to-import Grafana dashboard JSON is provided at
[`docs/grafana/jenkins-overview.json`](grafana/jenkins-overview.json).

### Import via the Grafana UI

1. Open Grafana and go to **Dashboards &rArr; Import**.
2. Click **Upload JSON file** and select `docs/grafana/jenkins-overview.json`.
3. Select your Prometheus datasource from the dropdown.
4. Click **Import**.

### Import via provisioning (recommended for production)

Copy the JSON file to your Grafana provisioning directory:

```bash
cp docs/grafana/jenkins-overview.json \
   /etc/grafana/provisioning/dashboards/jenkins-overview.json
```

Add a provisioning config if you don't already have one:

```yaml
# /etc/grafana/provisioning/dashboards/jenkins.yaml
apiVersion: 1
providers:
  - name: Jenkins
    type: file
    options:
      path: /etc/grafana/provisioning/dashboards
```

Restart Grafana. The dashboard appears automatically under **Dashboards**.

---

## Step 4 - Link Grafana back to Jenkins build pages (optional)

You can configure the plugin so that each Jenkins build page shows a
**"View in Grafana"** link. Use the `customObservabilityBackend` setting.

### Via the Jenkins UI

1. Go to **Manage Jenkins &rArr; Configure System &rArr; OpenTelemetry**.
2. Under **Visualization**, click **Add Observability Backend** &rArr;
   **Custom Observability Backend**.
3. Set **Metrics visualization URL template** to:
   ```
   http://your-grafana-host:3000/d/jenkins-overview/jenkins-overview?orgId=1&from=${startTime}&to=${endTime}
   ```
4. Click **Save**.

### Via JCasC

```yaml
unclassified:
  openTelemetry:
    authentication: "noAuthentication"
    endpoint: "otel-collector:4317"
    exportOtelConfigurationAsEnvironmentVariables: true
    observabilityBackends:
      - customObservabilityBackend:
          name: "Grafana"
          metricsVisualizationUrlTemplate: >
            http://your-grafana-host:3000/d/jenkins-overview/jenkins-overview
            ?orgId=1&from=${startTime}&to=${endTime}
```

---

## Dashboard panels

The `jenkins-overview.json` dashboard includes the following panels:

| Panel | Metric | Description |
|---|---|---|
| Build rate | `ci_pipeline_run_duration_milliseconds_count` | Builds per minute |
| Build duration (p50 / p95) | `ci_pipeline_run_duration_milliseconds` | Pipeline duration percentiles |
| Build success rate | `ci_pipeline_run_duration_milliseconds_count` filtered by `ci_pipeline_result="SUCCESS"` | Ratio of successful builds |
| Queue time (p50 / p95) | `ci_pipeline_run_queue_duration_milliseconds` | Time spent waiting in queue |
| Active executors | `jenkins_executor_count` | Executors in use vs available |
| Failed builds | `ci_pipeline_run_duration_milliseconds_count` filtered by `ci_pipeline_result="FAILURE"` | Count of failed builds |
| JVM heap usage | `runtime_jvm_memory_usage` | Jenkins controller JVM memory |
| HTTP request duration | `http_server_request_duration_seconds` | Jenkins controller HTTP latency |

---

## Troubleshooting

**No data in Grafana panels**

- Confirm Prometheus is scraping the Collector: open `http://prometheus:9090/targets`
  and check the `jenkins-otel` job is `UP`.
- Confirm `resource_to_telemetry_conversion.enabled: true` is set in the
  Collector config - without this, label filters in the dashboard panels
  will match nothing.
- Run at least one Jenkins build after configuring the plugin - metrics are
  only emitted when builds occur.

**`ci_pipeline_run_duration_milliseconds` missing**

This metric requires an allow-list to be set. By default it matches nothing.
Add to **Configuration Properties** in the plugin:

```
otel.instrumentation.jenkins.run.metric.duration.allow_list=.*
```

Or restrict to specific jobs:

```
otel.instrumentation.jenkins.run.metric.duration.allow_list=my-team/.*
```

See [monitoring-metrics.md](monitoring-metrics.md) for the full list of
configuration parameters that control metric cardinality.

**Dashboard shows data for wrong Jenkins instance**

If you run multiple Jenkins controllers, add `service.name` as a Grafana
variable to filter by instance. Each controller's metrics are labelled with
the `service_name` Prometheus label (derived from the OTel `service.name`
resource attribute).