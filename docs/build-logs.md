# Storing Jenkins Pipeline Logs in an Observability Backend though OpenTelemetry (version 2.0.0+)



## FAQ

### From where are the pipeline logs emitted? The Jenkins Controller? The Jenkins Agents? Both?

Pipeline logs are emitted both from the Jenkins controller and the Jenkins Agents according to where the code that emit logs is executing.
This means that the Jenkins agents establish a connection to the OpenTelemetry endpoint

### How are OpenTelemetry signals impacted by clock de-synchronization between the Jenkins controller and Jenkins Agents?

As The Jenkins OpenTelemetry logs integration evaluates the time offset between the system clocks on the Jenkins Controller and the Jenkins Agents. This offset is applied on the timestamp of the log messages emitted from the Jenkins Agents.
This means that the timestamp of log messages emitted on the Jenkins Agents doesn't rely on the system clock of the agent but on the time on the Jenkins Controller with a compensation.
This clock adjustment is required to display in the right ascending order the log messages.
Note that distributed traces don't require such a clock adjustment because all spans are emitted from the Jenkins Controller.