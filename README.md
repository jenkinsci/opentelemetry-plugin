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

<img alt="SpringBootPipeline Execution Trace" width="400px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-maven-execution-trace-jaeger.png" />

_Example pipeline execution trace of a SpringBoot app built with Maven going through security checks with Snyk, deployed on a Maven repository and published as a Docker image_


Visualize Jenkins and pipeline health indicators:

<img alt="Example Kibana dashboard of the Jenkins and CI jobs health" width="400px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/kibana_jenkins_overview_dashboard.png" />

_Example Kibana dashboard of the Jenkins and CI jobs health_


## Architecture

Using the [OpenTelemetry Collector](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases), you can use many monitoring backends to monitor Jenkins such as Jaeger, Zipkin, Prometheus, Elastic Observability and many others listed [here](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter).

Here are example architectures with Elastic, Jaeger, and Prometheus:

| CI/CD Observability with Jaeger and Prometheus | CI/CD Observability with Elastic |
|------------------------------------------------|----------------------------------|
|  <img alt="Jenkins monitoring with Jaeger and Prometheus" width="400" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-architecture-jaeger-prometheus.png" > | <img alt="Jenkins monitoring with Elastic Observability" width="400" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-architecture-elastic.png" > |

## Getting started

* Set up an OpenTelemetry endpoint such as the [OpenTelemetry Collector](https://github.com/open-telemetry/opentelemetry-collector-contrib)
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
* Set up Jenkins health dashboards on your OpenTelemetry metrics visualization solution. See details including guidance for Elastic Kibana [here](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/docs/monitoring-metrics.md).

<img alt="Sample Configuration" width="300px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-plugin-configuration.png" />

_Example Jenkins OpenTelemetry configuration_


## Setup and Configuration

For details to set up Jenkins with Elastic, Jaeger or Prometheus, to configure the integration including using Jenkins Configuration as Code, see [Setup and Configuration](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/docs/setup-and-configuration.md).


# Traces of Jobs and Pipelines Builds

For details on how to explore and troubleshoot jobs and pipelines builds as traces, see [Traces of Jobs and Pipeline Builds](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/docs/job-traces.md).

<img alt="SpringBootPipeline Execution Trace" width="300px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-maven-execution-trace-jaeger.png" />

_Example pipeline execution trace of a SpringBoot app built with Maven going through security checks with Snyk, deployed on a Maven repository and published as a Docker image_

# Storing Jenkins Pipeline Logs in an Observability Backend

For details on how to store Jenkins pipelines build logs in an Observability backend like Elastic, see [Storing Jenkins Pipeline Logs in an Observability Backend though OpenTelemetry](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/docs/build-logs.md).

<img alt="Storing Jenkins pipeline logs in Elasticsearch and visualizing logs both in Kibana and through Jenkins GUI" width="300px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-pipeline-logs-elastic-with-visualization-through-jenkins.png" />

_ Storing Jenkins pipeline logs in Elasticsearch and visualizing logs both in Kibana and through Jenkins GUI_

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

## Learn More

You can look at this video tutorial to get started: [![Tracing Your Jenkins Pipelines With OpenTelemetry and Jaeger](https://img.youtube.com/vi/3XzVOxvNpGM/0.jpg)](https://www.youtube.com/watch?v=3XzVOxvNpGM)

## Demos

If you'd like to see this plugin in action with some other integrations then refer to the [demos](demos/README.md).

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under Apache Software License 2, see [LICENSE](LICENSE)

## Learn More

* [DevOpsWorld 2021 - Embracing Observability in Jenkins with OpenTelemetry](https://www.devopsworld.com/agenda/session/581459)
