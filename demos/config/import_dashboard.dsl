NAME = 'import_dashboard'
DSL = """pipeline {
  agent any
  stages {
    stage('Import Dashboard') {
      steps {
        sh(label: 'Download dashboard', script: 'curl -sSfL -o dashboard.ndjson https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/main/src/main/kibana/jenkins-kibana-dashboards.ndjson')
        sh(label: 'Import Dashboard', script: 'curl -X POST -u "admin:changeme" http://kibana:5601/api/saved_objects/_import -H "kbn-xsrf: true" --form file=@dashboard.ndjson')
      }
    }
  }
}
"""

pipelineJob(NAME) {
  definition {
    cps {
      script(DSL.stripIndent())
    }
  }
}
