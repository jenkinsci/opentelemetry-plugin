NAME = 'my-otel-cli-make'
DSL = """pipeline {
  agent any
  environment {
    OTEL_LOCATION = pwd(tmp: true)
    OTEL_SERVICE_NAME = 'otel-cli'
  }
  stages {
    stage('checkout') {
      steps {
        writeFile(file: 'Makefile', text: '''.DEFAULT_GOAL := build
ifdef OTEL_EXPORTER_OTLP_ENDPOINT
export WRAPPER=otel-cli exec --name 'make-subcommand' --tp-print
else
export WRAPPER=
endif

.PHONY: build
build:
	\$(WRAPPER) echo build
	\$(WRAPPER) sleep 4
	\$(WRAPPER) echo 'mock a build'

.PHONY: test
test:
	\$(WRAPPER) echo test
	\$(WRAPPER) sleep 1
	\$(WRAPPER) echo 'mock a test' ''')
      }
    }
    stage('prepare') {
      steps {
        installOtelCli()
      }
    }
    stage('make build') {
      steps {
        withEnv(["PATH+OTEL=\${OTEL_LOCATION}"]) {
          sh(label: 'make build', script: "OTEL_EXPORTER_OTLP_ENDPOINT=\${env.OTEL_EXPORTER_OTLP_ENDPOINT.replaceAll('http://', '')} make build")
        }
      }
    }
    stage('make test') {
      steps {
        withEnv(["PATH+OTEL=\${OTEL_LOCATION}"]) {
          sh(label: 'make test', script: "OTEL_EXPORTER_OTLP_ENDPOINT=\${env.OTEL_EXPORTER_OTLP_ENDPOINT.replaceAll('http://', '')} make test")
        }
      }
    }
  }
}

def installOtelCli() {
  def os = 'Linux'
  if (sh(script: 'uname -a | grep -i Darwin', returnStatus: true) == 0) {
    os = 'Darwin'
  }
  dir("\${OTEL_LOCATION}") {
    sh(label: 'fetch otel-cli',
       script: "wget -q 'https://github.com/equinix-labs/otel-cli/releases/download/v0.0.18/otel-cli-0.0.18-\${os}-x86_64.tar.gz' -O otel-cli.tar.gz && tar -xf otel-cli.tar.gz")
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
