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
        installHermit()
      }
    }
    stage('run-ansible') {
      steps {
        script {
            runWithHermit(){
                sh(label: 'fetch the community.general collection', script: 'ansible-galaxy collection install community.general')
                sh(label: 'pip3 install', script: 'pip3 install opentelemetry-api opentelemetry-sdk opentelemetry-exporter-otlp')
                sh(label: 'run ansible', script: 'ansible-playbook playbook.yml')
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
            pip install ansible
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
