NAME = 'pytest-otel'
DSL = """pipeline {
  agent any
  stages {
    stage('checkout') {
      steps {
        git(url: 'https://github.com/kuisathaverat/pytest_otel.git', branch: 'main')
      }
    }
    stage('prepare-python') {
      steps {
        installHermit()
      }
    }
    stage('test') {
      steps {
        script {
            runWithHermit(){
                sh(label: 'dependencies', script: 'pip install ".[test]"')
                sh(label: 'pytest', script: 'python -m pytest --capture=no docs/demos/test/test_demo.py')
            }
        }
      }
    }
  }
}

def runWithHermit(Closure body){
    hermitEnvVars = sh(returnStdout: true, script: './hermit/bin/hermit env --raw').trim()
    withEnv(hermitEnvVars.split('\\n').toList()) {
        body()
    }
}

def installHermit() {
    sh(label: 'installHermit',
        script: 'curl -fsSL https://github.com/cashapp/hermit/releases/download/stable/install.sh | /bin/bash')
    sh(label: 'install tools',
        script: '''
            mkdir -p hermit
            cd hermit
            ~/bin/hermit init
            eval \$(./bin/hermit env --raw)
            hermit install python3
        ''')
}
"""

pipelineJob(NAME) {
  definition {
    cps {
      script(DSL.stripIndent())
    }
  }
}
