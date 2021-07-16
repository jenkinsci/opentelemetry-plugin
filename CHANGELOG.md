# Changes

## 0.10-beta (2021-05-07)

* More torubleshooting logs to understand and fix #87
* Report metric `jenkins.disk.usage.bytes` on the Jenkins controller file system usage for the `JENKINS_HOME` when using the [CloudBees Disk Usage Simple Plugin](https://plugins.jenkins.io/cloudbees-disk-usage-simple/)
* Allow customize the `service.name`and `service.namespace`

## 0.9.0 (2021-04-06)

* Better integration with Elastic Observability, set transaction type to `unknown` for consistency when using the Otel Collector exporter for Elastic and when using the Elastic native OTLP intake
* Better usage of OpenTelemetry Semantic Convention attributes for Git HTTPS and SSH access
* Better recovery of traces on ongoing pipelines upon Jenkins controller restarts
* Better JCasC support
* Support configuration of timeouts for the OTLP exporter


...

## 0.1-alpha (2021-02-22)

### Changes

First alpha release of the Jenkins OpenTelemetry plugin.

* Monitoring of job execution with a focus on pipeline jobs. Freestyle jobs are not detailed much
* Health metrics on
    * Jenkins build queue
    * Job executions
    * Authentication success/failure
* Built-in integration with [Elastic Observability](https://www.elastic.co/observability), [Jaeger](https://www.jaegertracing.io/), and [Zipkin](https://zipkin.io/).
  Other OpenTelemetry compatible distributed tracing and metrics solutions are also supported ([Prometheus](https://prometheus.io/)...). 
