# Traces of the Jenkins Controller HTTP Requests


Monitor and troubleshoot performance problems of the Jenkins Controller using tracing of HTTP requests.

* Analyze Jenkins controller HTTP requests performances


     <img alt="Jenkins Controller HTTP request trace with Jaeger" width="400px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/http-tracing/jenkins-http-request-trace-jaeger.png" /><br/><sub>Jenkins Controller HTTP request trace with Jaeger</sub><br/><br/>


* HTTP request traces can be disabled using the configuration:

```
otel.instrumentation.jenkins.web.enabled=false
```
* HTTP request parameters can be captured using the standard configuration parameter
 ([docs](https://opentelemetry.io/docs/zero-code/java/agent/configuration/#capturing-servlet-request-parameters)):

```
otel.instrumentation.servlet.experimental.capture-request-parameters=<<coma separated list of parameter names>>
```

* Observability solutions provide aggregated views on the overall activity on the Jenkins Controller UI, often enabling monitoring dashboards, alerting, and automated anomaly detection

     <img alt="Jenkins Controller HTTP request trace with Jaeger" width="400px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/http-tracing/jenkins-http-request-trace-elastic.png" /><br/><sub>Jenkins Controller HTTP request trace with Elastic</sub>

     <img alt="Jenkins Controller GUI overall health with Elastic" width="400px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/http-tracing/jenkins-http-request-traces-overview-elastic.png" /><br/><sub>Jenkins Controller GUI overall health with Elastic</sub><br/><br/>

     <img alt="Jenkins Controller GUI overall traces with Elastic" width="400px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/http-tracing/jenkins-http-request-traces-elastic.png" /><br/><sub>Jenkins Controller GUI overall traces with Elastic</sub><br/><br/>

     <img alt="Jenkins Controller HTTP request traces with Jaeger" width="400px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/http-tracing/jenkins-http-request-traces-jaeger.png" /><br/><sub>Jenkins Controller GUI traces with Jaeger</sub>
