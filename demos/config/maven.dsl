NAME = 'my-maven'
DSL = """pipeline {
  agent any
  environment {
    MAVEN_OPTS = '-Dmaven.ext.class.path=otel.jar'
    OTEL_VERSION = '0.1.0-beta-5'
  }
  stages {
    stage('checkout') {
      steps {
        git(url: 'https://github.com/elastic/opentelemetry-maven-extension', branch: 'main')
      }
    }
    stage('prepare') {
      steps {
        sh (label: 'fetch opentelemetry-maven-extension',
            script: 'curl -s https://repo.maven.apache.org/maven2/co/elastic/opentelemetry/contrib/opentelemetry-maven-extension/${OTEL_VERSION}/opentelemetry-maven-extension-${OTEL_VERSION}.jar > otel.jar')
      }
    }
    stage('compile') {
      steps {
        sh(label: 'mvn compile', script: './mvnw ${MAVEN_OPTS} clean compile')
      }
    }
    stage('test') {
      steps {
        sh(label: 'mvn compile', script: './mvnw ${MAVEN_OPTS} test')
      }
      post {
        always {
          junit 'target/surefire-reports/*.xml'
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
