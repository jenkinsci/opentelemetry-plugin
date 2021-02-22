# Changes

## 0.1-alpha (2021-02-22)

### Changes

First alpha release of the Jenkins OpenTelemetry plugin.

* Monitoring of job execution with a focus on pipeline jobs. Freestyle jobs are not detailed much
* Health metrics on
    * Jenkins build queue
    * Job executions
    * Authentication success/failure
* Built-in integration with [Elastic Observability](https://www.elastic.co/observability), [Jaeger](https://www.jaegertracing.io/), and [Zipkin](https://zipkin.io/).
  Other OpenTelemetry compatible distributed tracing and Metrics solutions are also supported ([Prometheus](https://prometheus.io/)...). 