# jenkins-opentelemetry

[![Build Status](https://ci.jenkins.io/job/Plugins/job/jenkins-opentelemetry-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/jenkins-opentelemetry-plugin/job/master/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/jenkins-opentelemetry-plugin.svg)](https://github.com/jenkinsci/jenkins-opentelemetry-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/jenkins-opentelemetry.svg)](https://plugins.jenkins.io/jenkins-opentelemetry)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/jenkins-opentelemetry-plugin.svg?label=changelog)](https://github.com/jenkinsci/jenkins-opentelemetry-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/jenkins-opentelemetry.svg?color=blue)](https://plugins.jenkins.io/jenkins-opentelemetry)

## Introduction

TODO Describe what your plugin does here

## Getting started

TODO Tell users how to configure your plugin here, include screenshots, pipeline examples and 
configuration-as-code examples.

## Screenshots

Sample of traces collected by Elastic APM for various pipelines

### Declarative Pipeline

![declarative-pipeline](https://github.com/cyrille-leclerc/opentelemetry-plugin/blob/master/docs/images/declarative-pipeline.png)

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


## Issues

TODO Decide where you're going to host your issues, the default is Jenkins JIRA, but you can also enable GitHub issues,
If you use GitHub issues there's no need for this section; else add the following line:

Report issues and enhancements in the [Jenkins issue tracker](https://issues.jenkins-ci.org/).

## Contributing

TODO review the default [CONTRIBUTING](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md) file and make sure it is appropriate for your plugin, if not then add your own one adapted from the base file

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

