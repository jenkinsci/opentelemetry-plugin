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
callbacks_enabled = community.general.opentelemetry
'''
      }
    }
    stage('run-ansible') {
      steps {
        script {
          // some magic with -u root:root to bypass the due to: 'getpwuid(): uid not found in ansible
          docker.image('geerlingguy/docker-ubuntu2004-ansible').inside('-u root:root --network demos_jenkins') {
            // some magic withEnv to bypass Permission denied: b'/.ansible'
            withEnv(["HOME=\${env.WORKSPACE}"]) {
              sh(label: 'fetch the community.general collection', script: 'ansible-galaxy collection install community.general')
              sh(label: 'pip3 install', script: 'pip3 install opentelemetry-api opentelemetry-sdk opentelemetry-exporter-otlp')
              sh(label: 'run ansible', script: 'ansible-playbook playbook.yml')
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
