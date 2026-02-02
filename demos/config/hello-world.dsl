NAME = 'hello_world'
DSL = """pipeline {
  agent any
  stages {
    stage('hello') {
      steps {
        echo 'Hello, world!'
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