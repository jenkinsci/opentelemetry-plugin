NAME = 'my-ansible'
DSL = """pipeline {
  agent any
  stages {
    stage('prepare-ansible') {
      steps {
        writeFile file: 'playbook.yml', text: '''
- name: Echo
  hosts: localhost
  connection: local

  tasks:
  - name: Print debug message
    debug:
      msg: Hello, world!
  - name: Create a file called '/tmp/testfile.txt' with the content 'hello world'.
    copy:
      content: hello world
      dest: /tmp/testfile.txt
'''
        writeFile file: 'ansible.cfg', text: '''
[defaults]
executable = /bin/bash
module_lang = en_US.UTF-8
callback_whitelist = opentelemetry
'''
        dir('callback_plugins') {
          sh (label: 'fetch ansible-plugin',
              script: 'curl -s https://raw.githubusercontent.com/v1v/community.general/feature/opentelemetry-callback-plugin-enhancement/plugins/callback/opentelemetry.py > opentelemetry.py')
        }
      }
    }
    stage('prepare-python-dependencies') {
      steps {
        sh(label: 'pip3 install', script: 'pip3 install opentelemetry-api opentelemetry-sdk opentelemetry-exporter-otlp')
      }
    }
    stage('run-ansible') {
      steps {
        sh(label: 'run ansible', script: 'ansible-playbook playbook.yml')
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
