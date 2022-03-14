# Configuration as Code (JCasC) - Jenkins OpenTelemetry Plugin

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
