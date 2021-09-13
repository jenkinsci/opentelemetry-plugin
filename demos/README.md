# Demos

This folder contains out of the boxes examples to illustrate how to use this plugin in conjunction with some other integrations.

## Context

This is an example of distributed tracing with Jenkins based on:

- [JCasC](https://jenkins.io/projects/jcasc/) to configure a local jenkins instance.
- [JobDSL](https://github.com/jenkinsci/job-dsl-plugin/wiki) to configure the pipelines to test the steps.
- [OpenTelemetry](https://github.com/jenkinsci/job-dsl-plugin/wiki) plugin to send traces :)
- [Maven](https://github.com/elastic/opentelemetry-maven-extension) to send traces for each maven goal.
- [Ansible](https://github.com/ansible-collections/community.general/pull/3091)
- [Gradle](https://github.com/jkwatson/gradle-otel-tracing)

## System Requirements

- Docker >= 19.x.x
- Docker Compose >= 1.25.0
- Java >= 8
- *nix based
- Python >=3.6
- Ansible

## Run this demo

0. Add entry in your etc/hosts

    ```
    echo '127.0.0.1 apm-server' | sudo tee -a /etc/hosts
    ```

1. Build docker image by running:

   ```
   make -C demos build
   ```

2. Start the local Jenkins master service by running:

   ```
   make -C demos start-all
   ```

3. Browse to <http://localhost:8080> in your web browser.


## Next

1. Decouple builds from the local agent, then the requirements can be simplify with only docker and java.
2. Support gradle integration with this plugin.
