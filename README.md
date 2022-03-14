# OpenTelemetry

[![Build Status](https://ci.jenkins.io/job/Plugins/job/opentelemetry-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/opentelemetry-plugin/job/master/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/opentelemetry-plugin.svg)](https://github.com/jenkinsci/opentelemetry-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/opentelemetry.svg)](https://plugins.jenkins.io/opentelemetry)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/opentelemetry-plugin.svg?label=changelog)](https://github.com/jenkinsci/opentelemetry-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/opentelemetry.svg?color=blue)](https://plugins.jenkins.io/opentelemetry)
[![Otel SDK](https://img.shields.io/badge/otel--sdk-1.12.0-blue?style=flat&logo=opentelemetry)](https://github.com/open-telemetry/opentelemetry-java/releases/tag/v1.12.0)

## Introduction

Monitor and observe Jenkins with OpenTelemetry.

Visualize jobs and pipelines executions as distributed traces:

![SpringBootPipeline Execution Trace](https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-maven-execution-trace-jaeger.png)
_Example pipeline execution trace of a SpringBoot app built with Maven going through security checks with Snyk, deployed on a Maven repository and published as a Docker image_


Visualize Jenkins and pipeline health indicators:
![Jenkins health dashboard](https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/kibana_jenkins_overview_dashboard.png)
_Example Kibana dashboard of the Jenkins and CI jobs health_


## Architecture

Using the [OpenTelemetry Collector](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases), you can use many monitoring backends to monitor Jenkins such as Jaeger, Zipkin, Prometheus, Elastic Observability and many others listed [here](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter).

Here are example architectures with Elastic, Jaeger, and Prometheus:

| CI/CD Observability with Jaeger and Prometheus | CI/CD Observability with Elastic |
|------------------------------------------------|----------------------------------|
|  <img alt="Jenkins monitoring with Jaeger and Prometheus" width="400" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-architecture-jaeger-prometheus.png" > | <img alt="Jenkins monitoring with Elastic Observability" width="400" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-architecture-elastic.png" > |

## Getting started

* Setup an OpenTelemetry endpoint such as the [OpenTelemetry Collector](https://github.com/open-telemetry/opentelemetry-collector-contrib)
* Install the Jenkins OpenTelemetry plugin
* Configure the Jenkins OpenTelemetry plugin navigating to the "Manage Jenkins / Configure System" screen. In the OpenTelemetry section define:
    * "OTLP Endpoint": the hostname and port of the OpenTelemetry GRPC Protocol (OTLP GRPC) endpoint, typically an OpenTelemetry Collector or directly an Observability backend that supports the OTLP GRPC protocol
    * "Authentication": authentication mechanism used by your OTLP Endpoint
        * "Header Authentication" : name of the authentication header if header based authentication is used.
        * "Bearer Token Authentication": Bearer token when using header based authentication. Note that Elastic APM token authentication uses a "Bearer Token Authentication".
        * "No Authentication"
    * Check "Export OpenTelemetry configuration as environment variables" to easily integrate visibility in other build tools (see the otel-cli, the OpenTelemetry Maven extension, the OpenTelemetry Ansible integration...)
    * Visualization: add the backend used to visualize job executions as traces.
        * Elastic Observability
        * Jaeger
        * Zipkin
        * Custom Observability backend for other visualization solutions
* You can now import Jenkins Health Dashboards in your preferred metrics visualization tool. See details in the [dashboard docs](docs/DASHBOARDS.md).

![Sample Configuration](https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-plugin-configuration.png)

You can look at this video tutorial to get started: [![Tracing Your Jenkins Pipelines With OpenTelemetry and Jaeger](https://img.youtube.com/vi/3XzVOxvNpGM/0.jpg)](https://www.youtube.com/watch?v=3XzVOxvNpGM)

## Advanced Configuration

| System property<br/>Environment Variable | Value | Description |
|------------------------------------------|-------|------------|
| otel.instrumentation.jenkins.job.dsl.collapse.job.name | Boolean, default `false` | When using Job DSL generated jobs, make the pipeline run root span name a low cardinality name using the name "Job from seed '${job.jobDslSeedJob.fullName}'" rather than using "${job.fullName}". Useful when the Job DSL plugin creates a lot of jobs |
| otel.instrumentation.jenkins.job.matrix.expand.job.name | Boolean, default `false` | When using Matrix Projects, the name of the combination jobs is by default collapsed to "${matrix-job-name}/execution" rather than using the full name that is generated joining the axis values of the combination |
| otel.instrumentation.jenkins.web.enabled | Boolean, default `true` | Since version 2.0.0. Disable the instrumentation of Jenkins web requests (ie the instrumentation of Jenkins Stapler) |


## Configuration as code

The Jenkins OpenTelemetry plugin supports configuration as code. The Elastic, Jaeger

Add to your yaml file:

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
              elasticsearchCredentialsId: "elasticsearch-logs-credentials"
              elasticsearchUrl: "https://***.cloud.es.io:9243"
      - jaeger:
          jaegerBaseUrl: "http://localhost:16686"
          name: "Jaeger"
    serviceName: "my-jenkins"
```

More details on [Jenkins OpenTelemetry plugin > Configuration as Code (JCasC)](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/docs/configuration-as-code.md)

## Setup

### Setup for Elastic

You just have to configure the Jenkins Controller to send OpenTelemetry data either directly to Elastic APM Server or via an OpenTelemetry Collector. Elastic handles both traces and metrics.

### Setup for Prometheus

Configure the Jenkins Controller to send OpenTelemetry data to an OpenTelemetry Collector and setup a [Prometheus exporter](https://github.com/open-telemetry/opentelemetry-collector/tree/main/exporter/prometheusexporter) on this collector.
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

### Configuring the Jenkins OpenTelemetry Plugin using OpenTelemetry standard environment variables and system properties

The configuration of the Jenkins OpenTelemetry plugin that relate to the export of the signals can be set up using the standard OpenTelemetry configuration environment variables and system properties.
The Jenkins OpenTelemetry plugin supports the following exporters: [OTLP](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#otlp-exporter-both-span-and-metric-exporters), [Jaeger](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#jaeger-exporter), [Prometheus](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#prometheus-exporter), and [Logging](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#logging-exporter). The logging exporter is a troubleshooting exporter than writes data to stdout.

When specifying configuration parameters mixing environment variables, system properties, and Jenkins Otel plugin config, the order of precedence is: Jenkins Plugin Config > JVM System Properties > Environment Variable.
Note that the key-value pairs of the `OTEL_RESOURCE_ATTRIBUTES` attributes are merged across all the layers of settings.

All the system properties and environment variables of the [OpenTelemetry SDK Auto Configuration Extension](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md) are supported at the exception of the settings of Zipkin exporter which is not included.


## Features

### Support for Jenkins pipelines and traditional Jobs

Support for Jenkins pipelines and traditional Jenkins jobs. For every executed step in a Jenkins Pipeline there is a span representation. A similar analogy for the the Jenkins traditional jobs (Freestyle, Matrix, Maven, and so on), therefore for every pre builder, builder and publisher step there is a span representation.

### Monitoring and troubleshooting Jenkins jobs using distributed tracing

* Understand where time is spent, including time spent waiting to schedule the job (time spent in the build queue)
   * The time spent in the built queue waiting for a build agent is visualised with the span "Phase : Start"
* Detect increasing time spent in steps like
   * Invocations of external systems (e.g. git checkout...)
* Built in integration with [Elastic Observability](https://www.elastic.co/observability), [Jaeger](https://www.jaegertracing.io/), and [Zipkin](https://zipkin.io/).
   Other OpenTelemetry compatible distributed tracing solutions are also supported.









## FAQ

### Enrich your pipeline `sh`, `bat`, and `powershell` steps with meaningful explanation thanks to labels

If you use Jenkins pipelines in conjunction with the `sh`, `bat`, `powershell` steps, then it's highly recommended using the `label` argument to add a meaningful explanation thanks to step labels. Example:

```groovy
node {
    sh(label: 'Maven verify', script: './mvnw deploy')
}
```

### Using the OpenTelemetry OTLP/HTTP rather than OTLP/GRPC protocol

Navigate to the Jenkins OpenTelemetry Plugin configuration, in the "Advanced" section, add to the "Configuration Properties text area the following:

```
otel.exporter.otlp.protocol=http/protobuf
```

## Demos

If you'd like to see this plugin in action with some other integrations then refer to the [demos](demos/README.md).

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under Apache Software License 2, see [LICENSE](LICENSE)

## Learn More

* [DevOpsWorld 2021 - Embracing Observability in Jenkins with OpenTelemetry](https://www.devopsworld.com/agenda/session/581459)
