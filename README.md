# OpenTelemetry

[![Build Status](https://ci.jenkins.io/job/Plugins/job/opentelemetry-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/opentelemetry-plugin/job/master/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/opentelemetry-plugin.svg)](https://github.com/jenkinsci/opentelemetry-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/opentelemetry.svg)](https://plugins.jenkins.io/opentelemetry)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/opentelemetry-plugin.svg?label=changelog)](https://github.com/jenkinsci/opentelemetry-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/opentelemetry.svg?color=blue)](https://plugins.jenkins.io/opentelemetry)

## Introduction

Collect Jenkins monitoring data through OpenTelemetry.

## Architecture

Using the [OpenTelemetry Connector](https://github.com/open-telemetry/opentelemetry-collector-contrib/releases), you can use many monitoring backends to monitor Jenkins such as Jaeger, Zipkin, Prometheus, Elastic Observability and many others listed [here](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter). 

Here are few examples of architecture:

<img alt="Jenkins monitoring with Elastic Observability" width="415" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-architecture-elastic.png" >  <img alt="Jenkins monitoring with Jaeger and Prometheus" width="415" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/jenkins-opentelemetry-architecture-jaeger-prometheus.png" >


## Features

### Monitoring of Jenkins jobs execution using distributed tracing principles

* Understand where time is spent, including time spent waiting to schedule the job (time spent in the build queue)
   * The time spent in the built queue waiting for a build agent is visualised with the span "Phase : Start"
* Detect increasing time spent in steps like 
   * Invocations of external systems (e.g. git checkout...)
* Built in integration with [Elastic Observability](https://www.elastic.co/observability), [Jaeger](https://www.jaegertracing.io/), and [Zipkin](https://zipkin.io/). 
   Other OpenTelemetry compatible distributed tracing solutions are also supported. 
   
### Metrics on Jenkins health indicators

* Jenkins health metrics
    * ci.pipeline.run.active 
    * ci.pipeline.run.launched 
    * ci.pipeline.run.started 
    * ci.pipeline.run.completed 
    * ci.pipeline.run.aborted 
    * jenkins.queue.waiting 
    * jenkins.queue.blocked 
    * jenkins.queue.buildable 
    * jenkins.queue.left 
    * jenkins.queue.time_spent_millis
* Jenkins metrics can be visualised with any OpenTelemetry compatible metrics solution such as [Prometheus](https://prometheus.io/) or [Elastic Observability](https://www.elastic.co/observability) 

## Getting started

* Setup an OpenTelemetry endpoint such as the [OpenTelemetry Collector](https://github.com/open-telemetry/opentelemetry-collector-contrib)
* Install the Jenkins OpenTelemetry plugin
* Configure the Jenkins OpenTelemetry plugin navigating to the "Manage Jenkins / Configure System" screen
* In the OpenTelemetry section define
  * "OTLP GRPC Endpoint": the hostname and port of the OpenTelemetry GRPC Protocol (OTLP GRPC) endpoint, typically an OpenTelemetry Collector or directly an Observability backend that supports the OTLP GRPC protocol
  * "Use TLS": check if your OTLP GRPC uses TLS
  * "GRPC Authentication Token Header" : name of the authentication header if header based authentication is used
  * "GRPC Authentication Token": token when using header based authentication
  * Visualization backend: the backend used to visualize job executions as traces.
    * Elastic Observability
    * Jaeger
    * Zipkin
    * Custom Observability backend for other visualisation solution

![Sample Configuration](https://raw.githubusercontent.com/cyrille-leclerc/opentelemetry-plugin/master/docs/images/jenkins-otel-plugin-configuration.png)  

## Screenshots

Sample of traces collected for various flavors of pipelines

### Scripted Pipeline

#### Scripted pipeline status page

```groovy
node {
    stage('Prepare') {
        echo("Prepare")
    }
    stage('Build') {
        git 'https://github.com/jglick/simple-maven-project-with-tests.git'
        sh "mvn -Dmaven.test.failure.ignore=true clean package"
    }
    stage('Post Build') {
        echo("this is the post build phase")
    }
}
```

![Scripted pipeline status page with Elastic Observability link](https://github.com/cyrille-leclerc/opentelemetry-plugin/blob/master/docs/images/scripted-pipeline-status-page-elastic-observability-annotated.jpg)


#### Scripted pipeline visualized with Elastic Observability 

![Scripted pipeline visualised with Elastic Observability](https://github.com/cyrille-leclerc/opentelemetry-plugin/blob/master/docs/images/scripted-pipeline-trace-elastic-observability.png)

#### Scripted pipeline visualized with Jaeger

![Scripted pipeline visualised with Jaeger](https://github.com/cyrille-leclerc/opentelemetry-plugin/blob/master/docs/images/scripted-pipeline-trace-jaeger.png)

#### Scripted pipeline visualized with Zipkin

![Scripted pipeline visualised with Jaeger](https://github.com/cyrille-leclerc/opentelemetry-plugin/blob/master/docs/images/scripted-pipeline-trace-zipkin.png)


### Declarative Pipeline

![Declarative pipeline visualised with Elastic Observability](https://github.com/cyrille-leclerc/opentelemetry-plugin/blob/master/docs/images/declarative-pipeline.png)

```groovy
pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                git 'https://github.com/jglick/simple-maven-project-with-tests.git'
                sh "mvn -Dmaven.test.failure.ignore=true clean package"
            }
            post {
                success {
                    echo "success"
                }
            }
        }
    }
}

```
### Scripted Pipeline with Error

![scripted-pipeline-with-error](https://github.com/cyrille-leclerc/opentelemetry-plugin/blob/master/docs/images/scripted-pipeline-with-error.png)

```
node {
    stage('Prepare') {
        echo("Prepare")
    }
    stage('Build') {
        git 'https://github.com/jglick/simple-maven-project-with-tests.git'
        sh "mvn -Dmaven.test.failure.ignore=true clean package"
    }
    stage('Post Build') {
        error 'Fail'
    }
}
```

### Scripted Pipeline with Parallel Step

![scripted-pipeline-with-parallel-step](https://github.com/cyrille-leclerc/opentelemetry-plugin/blob/master/docs/images/scripted-pipeline-with-parallel-step.png)

```
node {
    stage('Prepare') {
        echo("Prepare")
    }
    stage('Build') {
        git 'https://github.com/jglick/simple-maven-project-with-tests.git'
        sh "mvn -Dmaven.test.failure.ignore=true clean package"
    }
    stage('Parallel Post Build') {
        parallel parallBranch1: {
            echo("this is the post build parallel branch 1")
        } ,parallBranch2: {
            echo("this is the post build parallel branch 2")
            echo("this is the post build parallel branch 2")
        }
    }
}
```

### Freestyle Job

![freestyle-job](https://github.com/cyrille-leclerc/opentelemetry-plugin/blob/master/docs/images/freestyle-job.png)


### Ideas

* Collect labels of build agents
* Detect outages caused by upgrades. Report on the version of the plugin of each plugin being used as a step

## Configuration as code

This plugin supports configuration as code
Add to your yaml file:

```yaml
unclassified:
  openTelemetry:
    endpoint: "otel-collector-contrib:4317"
    observabilityBackends:
      - elastic:
          kibanaBaseUrl: "http://localhost:5601"
      - jaeger:
          jaegerBaseUrl: "http://localhost:16686"
      - customObservabilityBackend:
          metricsVisualisationUrlTemplate: "foo"
          traceVisualisationUrlTemplate: "http://example.com"
    useTls: false
```

For more details see the configuration as code plugin documentation:
<https://github.com/jenkinsci/configuration-as-code-plugin#getting-started>


## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under Apache Software License 2, see [LICENSE](LICENSE)

