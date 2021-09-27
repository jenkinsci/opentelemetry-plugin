NAME = 'my-gradle'
DSL = """pipeline {
  agent any
  environment {
    OTEL_SERVICE_NAME = 'gradle'
    OTEL_RESOURCE_ATTRIBUTES = 'service.name=gradle'
  }
  stages {
    stage('checkout') {
      steps {
        git(url: 'https://github.com/v1v/gradle-otel-tracing', branch: 'feature/add-otel-plugin')
      }
    }
    stage('build') {
      steps {
        script {
          docker.image('openjdk:8-jdk-alpine').inside('--network demos_jenkins') {
            withEnv(["HOME=\${env.WORKSPACE}"]) {
              sh(label: 'gradle build', script: './gradlew clean build')
            }
          }
        }
      }
    }
    stage('test') {
      steps {
        script {
          docker.image('openjdk:8-jdk-alpine').inside('--network demos_jenkins') {
            withEnv(["HOME=\${env.WORKSPACE}"]) {
              sh(label: 'gradle test', script: './gradlew clean test')
            }
          }
        }
      }
    }
  }
}"""

pipelineJob(NAME) {
  definition {
    cps {
      script(DSL.stripIndent())
    }
  }
}
