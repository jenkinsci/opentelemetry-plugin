# OpenTelemetry

[![Build Status](https://ci.jenkins.io/job/Plugins/job/opentelemetry-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/opentelemetry-plugin/job/master/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/opentelemetry-plugin.svg)](https://github.com/jenkinsci/opentelemetry-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/opentelemetry.svg)](https://plugins.jenkins.io/opentelemetry)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/opentelemetry-plugin.svg?label=changelog)](https://github.com/jenkinsci/opentelemetry-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/opentelemetry.svg?color=blue)](https://plugins.jenkins.io/opentelemetry)
[![Otel SDK](https://img.shields.io/badge/otel--sdk-1.11.0-blue?style=flat&logo=opentelemetry)](https://github.com/open-telemetry/opentelemetry-java/releases/tag/v1.11.0)

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

This plugin supports configuration as code. Add to your yaml file:

```yaml
unclassified:
  openTelemetry:
    authentication: "noAuthentication"
    endpoint: "otel-collector-contrib:4317"
    exportOtelConfigurationAsEnvironmentVariables: true
    exporterIntervalMillis: 60000
    exporterTimeoutMillis: 30000
    ignoredSteps: "dir,echo,isUnix,pwd,properties"
    observabilityBackends:
      - elastic:
          kibanaBaseUrl: "http://localhost:5601"
          name: "Elastic Observability"
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
    serviceName: "jenkins"
    serviceNamespace: "jenkins"
```

ℹ️ be careful of the misalignment of spelling between  `metricsVisualizationUrlTemplate` and `traceVisualisationUrlTemplate`.
This misalignment ("visualisation" versus "visualization") will be fixed soon aligning on "visualization" while supporting backward compatibility.

See the [jcasc](src/test/resources/io/jenkins/plugins/opentelemetry/jcasc) folder with various samples.

For more details see the configuration as code plugin documentation:
<https://github.com/jenkinsci/configuration-as-code-plugin#getting-started>

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

### Environment variables for trace context propagation and integrations

The context of the current span is exposed as environment variables to ease integration with third party tools.

* `TRACEPARENT`: the [W3C Trace Context header `traceparent`](https://www.w3.org/TR/trace-context-1/#traceparent-header)
* `TRACESTATE`: the [W3C Trace Context header `tracestate`](https://www.w3.org/TR/trace-context-1/#tracestate-header)
* `TRACE_ID`: the trace id of the job / pipeline
* `SPAN_ID`: the id of the pipeline shell step span

When the configuration options "Export OpenTelemetry configuration as environment variables", the following [OpenTelemetry environment variables](https://github.com/open-telemetry/opentelemetry-specification/blob/v1.6.0/specification/protocol/exporter.md) will be exported according to the settings of the plugin:
* `OTEL_EXPORTER_OTLP_ENDPOINT`: Target to which the exporter is going to send spans or metrics.
* `OTEL_EXPORTER_OTLP_INSECURE`: Whether to enable client transport security for the exporter's gRPC connection
* `OTEL_EXPORTER_OTLP_HEADERS`: Key-value pairs to be used as headers associated with gRPC or HTTP requests. Typically used to pass credentials.
* `OTEL_EXPORTER_OTLP_TIMEOUT`: Maximum time the OTLP exporter will wait for each batch export.
* `OTEL_EXPORTER_OTLP_CERTIFICATE`: The trusted certificate to use when verifying a server's TLS credentials.

In addition, if the backends were configured then there will be an environment variable for each of them pointing to the URL with the span/transactions:

* `OTEL_CUSTOM_URL`
* `OTEL_ELASTIC_URL`
* `OTEL_JAEGER_URL`
* `OTEL_ZIPKIN_URL`

#### Attributes

##### Pipeline, freestyle, and matrix project build spans

| Attribute                        | Description  | Type |
|----------------------------------|--------------|------|
| ci.pipeline.id                   | Job name | String |
| ci.pipeline.name                 | Job name (user friendly) | String |
| ci.pipeline.type                 | Job type | Enum (`freestyle`, `matrix`, `maven`, `workflow`, `multibranch`, `unknown`) |
| ci.pipeline.template.id          | Job DSL seed job full name. Added when the executed job has been generated by the [Job DSL plugin](https://plugins.jenkins.io/job-dsl/)| String |
| ci.pipeline.template.url         | Job DSL seed job URL. Added when the executed job has been generated by the [Job DSL plugin](https://plugins.jenkins.io/job-dsl/)| String |
| ci.pipeline.multibranch.type     | Multibranch type | Enum (`branch`, `tag`, `change_request`) |
| ci.pipeline.axis.names           | When using matrix projects, names of the axis of the job | String[] |
| ci.pipeline.axis.values           | When using matrix projects, values of the axis of the job | String[] |
| ci.pipeline.agent.id             | Name of the agent | String |
| ci.pipeline.run.completed        | Is this a complete build? | Boolean |
| ci.pipeline.run.durationMillis   | Build duration | Long |
| ci.pipeline.run.description      | Build description | String |
| ci.pipeline.run.number           | Build number | Long |
| ci.pipeline.run.result           | Build result | Enum (`aborted`, `success`, `failure`, `not_build` and `unstable`) |
| ci.pipeline.run.url              | Build URL | String |
| ci.pipeline.run.user             | Who triggered the build | String |
| ci.pipeline.run.cause            | List of machine-readable build causes like `UserIdCause:anonymous` or `BranchIndexingCause`. Pattern : `${cause.class.simpleName}[:details]` | String[] |
| ci.pipeline.run.committers       | List of users that caused the build. | String[] |
| ci.pipeline.parameter.sensitive  | Whether the information contained in this parameter is sensitive or security related. | Boolean[] |
| ci.pipeline.parameter.name       | Name of the parameters | String[] |
| ci.pipeline.parameter.value      | Value of the parameters. "Sensitive" values are redacted | String[] |

##### Pipeline step spans  

| Status Code | Status Description | Description |
|-------------|--------------------|-------------|
| OK | | for step and build success |
| UNSET | Machine readable status like `FlowInterruptedException:FailFastCause:Failed in branch failingBranch` | For interrupted steps of type fail fast parallel pipeline interruption, pipeline build superseded by a newer build, or pipeline build cancelled by user, the span status is set to `UNSET`  rather than `ERROR` for readability |
| ERROR | Machine readable status like `FlowInterruptedException:ExceededTimeout:Timeout has been exceeded` | For other causes of step failure |


| Attribute                        | Description  | Type |
|----------------------------------|--------------|------|
| jenkins.pipeline.step.name       | Step name (user friendly) | String |
| jenkins.pipeline.step.type       | Step name | String |
| jenkins.pipeline.step.id         | Step id   | String |
| jenkins.pipeline.step.plugin.name | Jenkins plugin for that particular step | String |
| jenkins.pipeline.step.plugin.version| Jenkins plugin version | String |
| jenkins.pipeline.step.agent.label | Labels attached to the agent | String |
| jenkins.pipeline.step.interruption.causes | List of machine readable causes of the interruption of the step like `FailFastCause:Failed in branch failingBranch`. <p/>Common causes of interruption:  `CanceledCause: Superseded by my-pipeline#123`, `ExceededTimeout: Timeout has been exceeded`, `FailFastCause:Failed in branch the-failing-branch`, `UserInterruption: Aborted by a-user` | String[] |
| git.branch                       | Git branch name | String |
| git.repository                   | Git repository | String |
| git.username                     | Git user | String |
| git.clone.shallow                | Git shallow clone | Boolean |
| git.clone.depth                  | Git shallow clone depth | Long |
| git.username                     | Git user | String |
| jenkins.url                      | Jenkins URL | String |
| jenkins.computer.name            | Name of the agent | String |

### Metrics on Jenkins health indicators

| Metrics                          | Unit  | Label key  | Label Value       | Description |
|----------------------------------|-------|------------|-------------------|-------------|
| ci.pipeline.run.active           | 1     |            |                   | Gauge of active jobs |
| ci.pipeline.run.launched         | 1     |            |                   | Job launched |
| ci.pipeline.run.started          | 1     |            |                   | Job started |
| ci.pipeline.run.completed        | 1     |            |                   | Job completed |
| ci.pipeline.run.aborted          | 1     |            |                   | Job aborted |
| jenkins.queue.waiting            | 1     |            |                   | Number of waiting items in queue |
| jenkins.queue.blocked            | 1     |            |                   | Number of blocked items in queue |
| jenkins.queue.buildable          | 1     |            |                   | Number of buildable items in queue |
| jenkins.queue.left               | 1     |            |                   | Total count of left items |
| jenkins.queue.time_spent_millis  | ms    |            |                   | Total time spent in queue by items |
| jenkins.agents.total             | 1     |            |                   | Number of agents|
| jenkins.agents.online            | 1     |            |                   | Number of online agents |
| jenkins.agents.offline           | 1     |            |                   | Number of offline agents |
| jenkins.agents.launch.failure    | 1     |            |                   | Number of failed launched agents |
| jenkins.cloud.agents.completed   | 1     |            |                   | Number of provisioned cloud agents |
| jenkins.cloud.agents.launch.failure | 1  |            |                   | Number of failed cloud agents |
| jenkins.disk.usage.bytes         | By    |            |                   | Disk Usage size |
| runtime.jvm.gc.time              | ms    |  gc        | `G1 Young Generation`, `G1 Old Generation...` | see [GarbageCollectorMXBean](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.management/com/sun/management/GarbageCollectorMXBean.html) |
| runtime.jvm.gc.count             | 1     |  gc        | `G1 Young Generation`, `G1 Old Generation...` | see [GarbageCollectorMXBean](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.management/com/sun/management/GarbageCollectorMXBean.html) |
| runtime.jvm.memory.area          | bytes | type, area | `used`, `committed`, `max`. <br/> `heap`, `non_heap` | see [MemoryUsage](https://docs.oracle.com/en/java/javase/11/docs/api/java.management/java/lang/management/MemoryUsage.html) |
| runtime.jvm.memory.pool          | bytes | type, pool | `used`, `committed`, `max`. <br/> `PS Eden Space`, `G1 Old Gen...` | see [MemoryUsage](https://docs.oracle.com/en/java/javase/11/docs/api/java.management/java/lang/management/MemoryUsage.html) |
| system.cpu.load                  | 1     |            |                  | System CPU load. See `com.sun.management.OperatingSystemMXBean.getSystemCpuLoad` |
| system.cpu.load.average.1m       | 1     |            |                  | System CPU load average 1 minute See `java.lang.management.OperatingSystemMXBean.getSystemLoadAverage` |
| system.memory.usage              | By    | state      | `used`, `free`   | see `com.sun.management.OperatingSystemMXBean.getTotalPhysicalMemorySize` and `com.sun.management.OperatingSystemMXBean.getFreePhysicalMemorySize` |
| system.memory.utilization        | 1     |            |                  | System memory utilization, see `com.sun.management.OperatingSystemMXBean.getTotalPhysicalMemorySize` and `com.sun.management.OperatingSystemMXBean.getFreePhysicalMemorySize`. Report `0%` if no physical memory is discovered by the JVM.|
| system.paging.usage              | By    | state      | `used`, `free`   | see `com.sun.management.OperatingSystemMXBean.getFreeSwapSpaceSize` and `com.sun.management.OperatingSystemMXBean.getTotalSwapSpaceSize` |
| system.paging.utilization        | 1     |            |                  | see `com.sun.management.OperatingSystemMXBean.getFreeSwapSpaceSize` and `com.sun.management.OperatingSystemMXBean.getTotalSwapSpaceSize`. Report `0%` if no swap memory is discovered by the JVM.|
| process.cpu.load                 | 1     |            |                  | Process CPU load. See `com.sun.management.OperatingSystemMXBean.getProcessCpuLoad` |
| process.cpu.time                 | ns    |            |                  | Process CPU time. See `com.sun.management.OperatingSystemMXBean.getProcessCpuTime` |


Jenkins' metrics can be visualised with any OpenTelemetry compatible metrics solution such as [Prometheus](https://prometheus.io/) or [Elastic Observability](https://www.elastic.co/observability)




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
