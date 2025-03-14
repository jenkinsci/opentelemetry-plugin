# Demos

This folder contains out of the boxes examples to illustrate how to use this plugin in conjunction with some other integrations.

The demo exposes the following backends:

- Elastic at http://0.0.0.0:5601
- Jenkins at http://0.0.0.0:8080
- Jaeger at http://0.0.0.0:16686
- Zipkin at http://0.0.0.0:9411
- Prometheus at http://0.0.0.0:9090

## Context

This is an example of distributed tracing with Jenkins based on:

- [JCasC](https://jenkins.io/projects/jcasc/) to configure a local jenkins instance.
- [JobDSL](https://github.com/jenkinsci/job-dsl-plugin/wiki) to configure the pipelines to test the steps.
- [OpenTelemetry](https://github.com/jenkinsci/job-dsl-plugin/wiki) plugin to send traces :)
- [Maven](https://github.com/elastic/opentelemetry-maven-extension) to send traces for each maven goal.
- [Ansible](https://github.com/ansible-collections/community.general/pull/3091) to send traces for each ansible task.
- [Gradle](https://github.com/jkwatson/gradle-otel-tracing) to send traces for each gradle task.
- [OtelCli](https://github.com/equinix-labs/otel-cli) to send traces for command.

## System Requirements

- Docker >= 19.x.x (make sure you have greater than 2gb memory allocated to Docker)
- Docker Compose >= 1.25.0
- Java >= 8
- *nix based (preferably x86_64)

If `ARM64` please read the [support for `ARM64`](#support-for-arm64) section.

## Run this demo


1. Build docker image by running:

   ```
   make -C demos build
   ```

2. Start the local Jenkins master service by running:

   ```
   make -C demos start
   ```

3. Browse to <http://localhost:8080> in your web browser.


## Next

1. Support gradle integration with this plugin.

## Further details

It uses the OpenTelemetry Collector to send traces and metrics to different vendors, see https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/examples/demo

### Support for ARM64

The docker image of the OpenTelemetry Collector does not support ARM64 arch yet, though there is a workaround, see https://github.com/open-telemetry/opentelemetry-collector-contrib/issues/2379#issuecomment-781745225

If you'd like to use it then:

1. Create a Dockerfile in `demos/` with the content explained in the above comment.
2. Edit `demos/docker-compose.yml` and replace the `otel-collector` service with the `build` command.
3. Proceed with the `Run this demo` section.
