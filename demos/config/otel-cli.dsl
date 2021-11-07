NAME = 'my-otel-cli'
DSL = """pipeline {
  agent any
  environment {
    OTEL_LOCATION = pwd(tmp: true)
    OTEL_SERVICE_NAME = 'otel-cli'
  }
  stages {
    stage('checkout') {
      steps {
        writeFile(file: 'Makefile', text: '''.DEFAULT_GOAL := all
.PHONY: build
build:
	@echo build

.PHONY: test
test:
	@echo test''')
      }
    }
    stage('prepare') {
      steps {
        installOtelCli()
      }
    }
    stage('make build') {
      steps {
        runWithOtelCli(label: 'make build', script: 'make build')
      }
    }
    stage('make test') {
      steps {
        runWithOtelCli(label: 'make test', script: 'make test')
      }
    }
  }
}

def runWithOtelCli(def args=[:]) {
  withEnv(["PATH+OTEL=\${OTEL_LOCATION}"]) {
    sh 'env | sort'
    sh(label: args.label, script: "OTEL_EXPORTER_OTLP_ENDPOINT=\${env.OTEL_EXPORTER_OTLP_ENDPOINT.replaceAll('http://', '')} otel-cli exec --name '\${args.label}'--tp-print \${args.script}")
  }
}

def installOtelCli() {
  def os = 'Linux'
  if (sh(script: 'uname -a | grep -i Darwin', returnStatus: true) == 0) {
    os = 'Darwin'
  }
  dir("\${OTEL_LOCATION}") {
    sh(label: 'fetch otel-cli',
       script: "wget 'https://github.com/equinix-labs/otel-cli/releases/download/v0.0.18/otel-cli-0.0.18-\${os}-x86_64.tar.gz' -O otel-cli.tar.gz && tar -xf otel-cli.tar.gz")
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
