# Setup and Configuration - Jenkins OpenTelemetry Plugin

## Setup

### Setup for Elastic

Elastic handles both traces, metrics and logs. You just have to configure the Jenkins Controller to send OpenTelemetry data either directly to Elastic APM Server or via an OpenTelemetry Collector. 
ℹ️ Sending logs to Elastic requires Elastic v8.1.0+.

For better scalability, particularly when sending logs to Elastic, it is recommended to deploy an OpenTelemetry Collector alongside the CI infrastructure

<img width="400px"
alt="Configuration - Elastic Observability Backend - Advanced configuration"
src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/better-docs/docs/images/jenkins-config-elastic-logs-with-visualization-through-jenkins.png" />

_Jenkins OpenTelemetry set up with Elastic including storing logs in Elastic visualizing logs both in Elastic Kibana and through Jenkins_

```yaml
exporters:
  otlp/elastic:
    endpoint: "***.apm.***.cloud.es.io:443"
    headers:
      Authorization: "Bearer <<my secret token>>>>"
    ...
service:
  pipelines:
    traces:
      receivers:
        - otlp
      exporters:
        - otlp/elastic
    metrics:
        receivers:
            - otlp
        exporters:
            - otlp/elastic
    logs:
        receivers:
            - otlp
        exporters:
            - otlp/elastic # requires Elastic v8.1+
    ...
```
_OpenTelemetry Collector configuration to export traces, metrics, and logs to Elastic_

| Jenkins Monitoring with Elastic | Jenkins Monitoring with Elastic and OpenTelemetry Collector |
|------------------------------------------------|----------------------------------|
|  <img alt="Jenkins monitoring with Elastic Observability" width="400" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-architecture-elastic.png" > | <img alt="Jenkins monitoring with Elastic Observability" width="400" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-architecture-collector-elastic.png" > |


### Setup for Prometheus

Configure the Jenkins Controller to send OpenTelemetry data to an OpenTelemetry Collector and define on this OpenTelemetry a [Prometheus exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/prometheusexporter) on this collector.

ℹ️ Note that Prometheus only supports metrics; traces and logs require other observability backends.

ℹ️ Enable `resource_to_telemetry_conversion` on the OpenTelemetry Collector exporter for Prometheus in order to have the OpenTelemetry metrics resource attributes converted to Prometheus labels to differentiate the different Jenkins Controllers.

```yaml
exporters:
  prometheus:
    endpoint: 0.0.0.0:1234
    resource_to_telemetry_conversion:
      enabled: true
  ...
service:
  pipelines:
    metrics:
      receivers:
        - otlp
      exporters:
        - prometheus
    traces:
      ...
```
_OpenTelemetry Collector configuration to export metric to Prometheus_


### Setup for Jaeger

Configure the Jenkins Controller to send OpenTelemetry data to an OpenTelemetry Collector and define on this OpenTelemetry a [Jaeger exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/jaegerexporter) on this collector.

ℹ️ Note that Jaeger only supports traces; metrics and logs require other observability backends.

```yaml
exporters:
  jaeger:
      endpoint: localhost:14250
      ...
  ...
service:
  pipelines:
    traces:
      receivers:
        - otlp
      exporters:
        - jaeger
    ...
```
_OpenTelemetry Collector configuration to export traces to Jaeger_

<img alt="Jenkins monitoring with Jaeger and Prometheus" width="400" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-architecture-jaeger-prometheus.png" >

_Jenkins monitoring with Jaeger and Prometheus_

## Advanced Configuration  Settings 

Advanced configuration settings

| System property<br/>Environment Variable | Value                    | Description                                                                                                                                                                                                                                            |
|------------------------------------------|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| otel.instrumentation.jenkins.job.dsl.collapse.job.name | Boolean, default `false` | When using Job DSL generated jobs, make the pipeline run root span name a low cardinality name using the name "Job from seed '${job.jobDslSeedJob.fullName}'" rather than using "${job.fullName}". Useful when the Job DSL plugin creates a lot of jobs |
| otel.instrumentation.jenkins.job.matrix.expand.job.name | Boolean, default `false` | When using Matrix Projects, the name of the combination jobs is by default collapsed to "${matrix-job-name}/execution" rather than using the full name that is generated joining the axis values of the combination                                    |
| otel.instrumentation.jenkins.web.enabled | Boolean, default `true`  | Since version 2.0.0. Disable the instrumentation of Jenkins web requests (ie the instrumentation of Jenkins Stapler)                                                                                                                                   |
| otel.instrumentation.jenkins.remote.span.enabled | Boolean, default `false` | Since version 2.17.0. When enabled, trace context is propagated when build is trigged by Jenkins HTTP API calls https://www.w3.org/TR/trace-context/                                                                                                                                        |                                                                                                 

## Configuration as Code (JCasC) - Jenkins OpenTelemetry Plugin

The Jenkins OpenTelemetry plugin supports configuration as code using the [Jenkins Configuration as Code](https://www.jenkins.io/projects/jcasc/) (aka JCasC).

Example:

```yaml
unclassified:
  openTelemetry:
    authentication: "noAuthentication"
    endpoint: "otel-collector-contrib:4317"
    exportOtelConfigurationAsEnvironmentVariables: true
    ignoredSteps: "dir,echo,isUnix,pwd,properties"
    observabilityBackends:
      - elastic:
          kibanaBaseUrl: "http://localhost:5601"
          name: "Elastic Observability"
          displayKibanaDashboardLink: true
          elasticLogsBackend:
            elasticLogsBackendWithJenkinsVisualization:
              elasticsearchCredentialsId: "elasticsearch-logs-creds"
              elasticsearchUrl: "https://***.cloud.es.io:9243"
      - jaeger:
          jaegerBaseUrl: "http://localhost:16686"
          name: "Jaeger"
      - customObservabilityBackend:
          metricsVisualizationUrlTemplate: "foo"
          traceVisualisationUrlTemplate: "http://example.com"
          name: "Custom Observability"
      - zipkin:
          zipkinBaseUrl: "http://localhost:9411/"
          name: "Zipkin"
    serviceName: "my-jenkins"
```

ℹ️ be careful of the misalignment of spelling between  `metricsVisualizationUrlTemplate` and `traceVisualisationUrlTemplate`.
This misalignment ("visualisation" versus "visualization") will be fixed soon aligning on "visualization" while supporting backward compatibility.

See the [jcasc](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/src/test/resources/io/jenkins/plugins/opentelemetry/jcasc) folder with various samples.

For more details see the configuration as code plugin documentation:
<https://github.com/jenkinsci/configuration-as-code-plugin#getting-started>

### Jaeger (traces)

See this [jcasc](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/src/test/resources/io/jenkins/plugins/opentelemetry/jcasc/jaeger.yml) about configuring Jaeger for traces.

### Elastic (traces, metrics, and logs)

#### Elastic for traces and metrics

See this [jcasc](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/src/test/resources/io/jenkins/plugins/opentelemetry/jcasc/elastic.yml) about configuring Elastic for traces and metrics.

#### Elastic for traces, metrics and logs (Jenkins OpenTelemetry Plugin v2.0.0+ and Elastic v8.1.0+)
See this [jcasc](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/src/test/resources/io/jenkins/plugins/opentelemetry/jcasc/elastic-logs.yml) about configuring Elastic for traces, metrics and logs (visualizing logs both in Elastic Kibana and through Jenkins).

See this [jcasc](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/src/test/resources/io/jenkins/plugins/opentelemetry/jcasc/elastic-logs-exclusive.yml) about configuring Elastic for traces, metrics and logs (exclusively visualizing logs in Elastic Kibana).

### Zipkin (traces)

See this [jcasc](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/src/test/resources/io/jenkins/plugins/opentelemetry/jcasc/zipkin.yml) about configuring Zipkin for traces.

## Configuring the Jenkins OpenTelemetry Plugin using OpenTelemetry standard environment variables and system properties

The configuration of the Jenkins OpenTelemetry plugin that relate to the export of the signals can be set up using the standard OpenTelemetry configuration environment variables and system properties.
The Jenkins OpenTelemetry plugin supports the following exporters: [OTLP](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#otlp-exporter-both-span-and-metric-exporters), [Jaeger](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#jaeger-exporter), [Prometheus](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#prometheus-exporter), and [Logging](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#logging-exporter). ℹ️ The logging exporter is a troubleshooting exporter that writes data to stdout.

When specifying configuration parameters mixing environment variables, system properties, and Jenkins Otel plugin config, the order of precedence is: Jenkins Plugin Config > JVM System Properties > Environment Variable.
Note that the key-value pairs of the `OTEL_RESOURCE_ATTRIBUTES` attributes are merged across all the layers of settings.

All the system properties and environment variables of the [OpenTelemetry SDK Auto Configuration Extension](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md) (`OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_METRICS_EXPORTER`, `OTEL_LOGS_EXPORTER`, `OTEL_RESOURCE_ATTRIBUTES`...) are supported at the exception to the settings of Zipkin exporter which is not included.

## TCP Keepalive

TCP Keepalive is enabled by default in the Elasticsearch connections, a keepalive is sent every 30 seconds.
It is possible to change this behaviout by using system properties.
To disable keepalive, set `io.jenkins.plugins.opentelemetry.backend.elastic.ElasticsearchLogStorageRetriever.keepAlive.enabled` to `false`, to change the keepalive interval, set `io.jenkins.plugins.opentelemetry.backend.ElasticsearchLogStorageRetriever.elastic.keepAlive.interval` to the desired value in miliseconds.

The following command disables the keepalive

```shell
java -Dio.jenkins.plugins.opentelemetry.backend.elastic.ElasticsearchLogStorageRetriever.keepAlive.enabled=false -jar jenkins.war
```

The following command changes the keepalive interval to 10 seconds

```shell
java -Dio.jenkins.plugins.opentelemetry.backend.elastic.ElasticsearchLogStorageRetriever.keepAlive.interval=10000 -jar jenkins.war
```

## Remote Trace Context Propagation

Since version 2.17.0, the Jenkins OpenTelemetry plugin supports remote trace context propagation when a build is triggered by Jenkins HTTP API calls. This feature is based on the [W3C Trace Context](https://www.w3.org/TR/trace-context/) standard.

To enable this feature, set the property `otel.instrumentation.jenkins.remote.span.enabled` to `true`. The Jenkins OpenTelemetry properties can be set either through the plugin configuration screen ("Advanced / Configuration Properties" section) or as a JVM system property.

```shell
java -Dotel.instrumentation.jenkins.remote.span.enabled=true -jar jenkins.war
```

Then from your remote system, you can trigger a build using the Jenkins REST API and include the trace context headers in the request. The Jenkins OpenTelemetry plugin will propagate the trace context to the build.

```shell
curl -X POST http://jenkins.example.com/job/my-job/build \
  -H "Jenkins-Crumb: 1234567890abcdef" \
  -H "traceparent: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01" 
```

## Using the OpenTelemetry OTLP/HTTP rather than OTLP/GRPC protocol

Navigate to the Jenkins OpenTelemetry Plugin configuration, in the "Advanced" section, add to the "Configuration Properties text area the following:

```
otel.exporter.otlp.protocol=http/protobuf
```

