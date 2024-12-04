# Jenkins Monitoring Dashboards and Health Metrics

Jenkins' metrics can be visualised with any OpenTelemetry compatible metrics solution such
as [Prometheus](https://prometheus.io/) or [Elastic Observability](https://www.elastic.co/observability)

## Jenkins Health Dashboards

The Jenkins OpenTelemetry integration provides all the key health metrics to monitor Jenkins with dashboards and alerts.

![Jenkins health dashboard](https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/kibana_jenkins_overview_dashboard.png)
_Example Kibana dashboard of the Jenkins and CI jobs health_

### Jenkins Health Dashboards with Elastic and Kibana

Monitor Jenkins with Elastic Observability importing the dashboard
definitions [jenkins-kibana-dashboards.ndjson](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/src/main/kibana/jenkins-kibana-dashboards.ndjson)
in Kibana (v7.12+).

Dashboards can be imported in Kibana using the Kibana
GUI ([here](https://www.elastic.co/guide/en/kibana/7.12/managing-saved-objects.html#managing-saved-objects-export-objects))
or APIs ([here](https://www.elastic.co/guide/en/kibana/current/dashboard-import-api.html)).

|  Jenkins and CI jobs health |  Jenkins Agent provisioning health |
|------------------------------------------------|----------------------------------|
| <img alt="Jenkins Health Dashboard with Elastic Kibana" width="300px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/kibana_jenkins_overview_dashboard.png" /> | <img alt="Jenkins Agent Provisioning Health Dashboard with Elastic Kibana" width="300px" src="https://raw.githubusercontent.com/jenkinsci/opentelemetry-plugin/master/docs/images/kibana_jenkins_provisioning_dashboard.png" /> |

## Build Duration

**⚠️ In order to control metrics cardinality, the `ci.pipeline.run.duration` metrics are enabled by default 
aggregating the durations of all the jobs/pipelines under the umbrella `ci.pipeline.id=#other#`.
To enable per job/pipeline metrics, use the allow and deny list setting the configuration parameters 
`otel.instrumentation.jenkins.run.metric.duration.allow_list` and `otel.instrumentation.jenkins.run.metric.duration.deny_list`.**

* Name: `ci.pipeline.run.duration`
* Type: Histogram with buckets: `1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192` (buckets subject to change)
* Unit: `s`
* Attributes:
  * `ci.pipeline.id`: The full name of the Jenkins job if complying with the allow and deny lists specified through 
    configuration parameters documented below, otherwise `#other#` to limit the cardinality of the metric. 
  Example: `my-team/my-app/main`. See `hudson.model.AbstractItem#getFullName()`.
  * `ci.pipeline.result`: `SUCCESS`, `UNSTABLE`, `FAILUIRE`, `NOT_BUILT`, `ABORTED`. See `hudson.model.Run#getResult()`.
* Configuration parameters to control the cardinality of the `ci.pipeline.id` attribute:
  * `otel.instrumentation.jenkins.run.metric.duration.allow_list`: Java regex, default value: `$^` (ie impossible regex matching nothing). Example `jenkins_folder_a/.*|jenkins_folder_b/.*`
  * `otel.instrumentation.jenkins.run.metric.duration.deny_list`: Java regex, default value: `$^` (ie impossible regex matching nothing). Example `.*test.*`

## Jenkins Build & Health Metrics

Inventory of health metrics collected by the Jenkins OpenTelemetry integration:
<table>
    <tr>
        <th>Metric</th>
        <th>Unit</th>
        <th>Attribute Key</th>
        <th>Attribute value</th>
        <th>Description</th>
    </tr>
    <tr>
        <td>ci.pipeline.run.duration</td>
        <td><code>s</code></td>
        <td></td>
        <td></td>
        <td>Duration of runs</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.active</td>
        <td><code>{jobs}</code></td>
        <td></td>
        <td></td>
        <td>Gauge of active jobs</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.active</td>
        <td><code>{jobs}</code></td>
        <td></td>
        <td></td>
        <td>Gauge of active jobs</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.launched</td>
        <td><code>{jobs}</code></td>
        <td></td>
        <td></td>
        <td>Job launched</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.started</td>
        <td><code>{jobs}</code></td>
        <td></td>
        <td></td>
        <td>Job started</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.completed</td>
        <td><code>{jobs}</code></td>
        <td></td>
        <td></td>
        <td>Job completed</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.aborted</td>
        <td><code>{jobs}</code></td>
        <td></td>
        <td></td>
        <td>Job aborted</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.success</td>
        <td><code>{jobs}</code></td>
        <td></td>
        <td></td>
        <td>Job successful</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.failed</td>
        <td><code>{jobs}</code></td>
        <td></td>
        <td></td>
        <td>Job failed</td>
    </tr>
    <tr>
        <td>jenkins.executor</td>
        <td><code>${executors}</code></td>
        <td>
            <code>label</code>,<br/>
            <code>status</code>
        </td>
        <td>
            Jenkins build agent <code>label</code>code> like <code>linux</code><br/>
            <code>busy</code>, <code>idle</code>, <code>connecting</code>
        </td>
        <td>
            Jenkins executors broken down by <code>label</code> and <code>status</code>. Executors annotated with 
            multiple <code>label</code> are reported multiple times
        </td>
    </tr>
    <tr>
        <td>jenkins.executor.total</td>
        <td><code>${executors}</code></td>
        <td>
            <code>status</code>
        </td>
        <td>
            Jenkins build agent <code>label</code>code> like <code>linux</code><br/>
            <code>busy</code>, <code>idle</code>
        </td>
        <td>Jenkins executors broken down by <code>status</code></td>
    </tr>
    <tr>
        <td>jenkins.node</td>
        <td><code>${nodes}</code></td>
        <td>
            <code>status</code>
        </td>
        <td>
            <code>online</code>, <code>offline</code>
        </td>
        <td>Jenkins build nodes</td>
    </tr>
    <tr>
        <td>jenkins.executor.available</td>
        <td><code>${executors}</code></td>
        <td><code>label</code></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.executor.busy</td>
        <td><code>${executors}</code></td>
        <td><code>label</code></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.executor.idle</td>
        <td><code>${executors}</code></td>
        <td><code>label</code></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.executor.online</td>
        <td><code>${executors}</code></td>
        <td><code>label</code></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.executor.connecting</td>
        <td><code>${executors}</code></td>
        <td><code>label</code></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.executor.defined</td>
        <td><code>${executors}</code></td>
        <td><code>label</code></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.executor.queue</td>
        <td><code>${items}</code></td>
        <td><code>label</code></td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.queue</td>
        <td><code>${tasks}</code></td>
        <td><code>status</code></td>
        <td>
            <code>blocked</code>, <code>buildable</code>, <code>stuck</code>, <code>waiting</code>, <code>unknown</code>
        </td>
        <td>Number of tasks in the queue. See <code>status</code>code> description [here](https://javadoc.jenkins.io/hudson/model/Queue.html)</td>
    </tr>
    <tr>
        <td>jenkins.queue.waiting</td>
        <td><code>${items}</code></td>
        <td></td>
        <td></td>
        <td>Number of tasks in the queue with the status 'buildable' or 'pending' (see <a href="https://javadoc.jenkins.io/hudson/model/Queue.html#getUnblockedItems--"><code>Queue#getUnblockedItems()</code></a>)</td>
    </tr>
    <tr>
        <td>jenkins.queue.blocked</td>
        <td><code>${items}</code></td>
        <td></td>
        <td></td>
        <td>Number of blocked tasks in the queue. Note that waiting for an executor to be available is not a reason to be counted as blocked. (see <a href="https://javadoc.jenkins.io/hudson/model/queue/QueueListener.html"><code>QueueListener#onEnterBlocked() - QueueListener#onLeaveBlocked()</code></a>)</td>
    </tr>
    <tr>
        <td>jenkins.queue.buildable</td>
        <td><code>${items}</code></td>
        <td></td>
        <td></td>
        <td>Number of tasks in the queue with the status 'buildable' or 'pending' (see <a href="https://javadoc.jenkins.io/hudson/model/Queue.html#getBuildableItems--"><code>Queue#getBuildableItems()</code></a>)</td>
    </tr>
    <tr>
        <td>jenkins.queue.left</td>
        <td><code>${items}</code></td>
        <td></td>
        <td></td>
        <td>Total count of tasks that have been processed (see [`QueueListener#onLeft`]()-</td>
    </tr>
    <tr>
        <td>jenkins.queue.time_spent_millis</td>
        <td><code>ms</code></td>
        <td></td>
        <td></td>
        <td>Total time spent in queue by the tasks that have been processed (see <a href="https://javadoc.jenkins.io/hudson/model/queue/QueueListener.html#onLeft-hudson.model.Queue.LeftItem-"><code>QueueListener#onLeft()</code></a> and <a href="https://javadoc.jenkins.io/hudson/model/Queue.Item.html#getInQueueSince--"><code>Item#getInQueueSince()</code></a>)</td>
    </tr>
    <tr>
        <td>jenkins.disk.usage.bytes</td>
        <td><code>By</code></td>
        <td></td>
        <td></td>
        <td>Disk Usage size</td>
    </tr>
    <tr>
        <td>http.server.request.duration</td>
        <td><code>s</code></td>
        <td>
            <code>http.request.method</code>,<br/>
            <code>url.scheme</code>,<br/>
            <code>error.type</code>, <br/>
            <code>http.response.status_code</code>, <br/>
            <code>http.route</code>, <br/>
            <code>server.address</code>, <br/>
            <code>server.port</code>
        </td>
        <td></td>
        <td>Disk Free size</td>
    </tr>
    <tr>
        <td>jenkins.plugins</td>
        <td><code>${plugins}</code></td>
        <td><code>status</code></td>
        <td><code>active</code>, <code>inactive</code>, <code>failed</code></td>
        <td>Jenkins plugins broken down by <code>status</code></td>
    </tr>
</table>

## Jenkins agents metrics

<table>
    <tr>
        <th>Metric</th>
        <th>Unit</th>
        <th>Attribute Key</th>
        <th>Attribute value</th>
        <th>Description</th>
    </tr>
    <tr>
        <td>jenkins.agents.total</td>
        <td><code>{agents}</code></td>
        <td></td>
        <td></td>
        <td>Number of agents</td>
    </tr>
    <tr>
        <td>jenkins.agents.online</td>
        <td><code>{agents}</code></td>
        <td></td>
        <td></td>
        <td>Number of online agents</td>
    </tr>
    <tr>
        <td>jenkins.agents.offline</td>
        <td><code>{agents}</code></td>
        <td></td>
        <td></td>
        <td>Number of offline agents</td>
    </tr>
    <tr>
        <td>jenkins.agents.launch.failure</td>
        <td><code>{agents}</code></td>
        <td></td>
        <td></td>
        <td>Number of failed launched agents</td>
    </tr>
    <tr>
        <td>jenkins.cloud.agents.completed</td>
        <td><code>{agents}</code></td>
        <td></td>
        <td></td>
        <td>Number of provisioned cloud agents</td>
    </tr>
    <tr>
        <td>jenkins.cloud.agents.launch.failure</td>
        <td><code>{agents}</code></td>
        <td></td>
        <td></td>
        <td>Number of failed cloud agents</td>
    </tr>
</table>

## SCM metrics (SCM event queue, GitHub client API rate limit...)

<table>
    <tr>
        <th>Metric</th>
        <th>Unit</th>
        <th>Attribute Key</th>
        <th>Attribute value</th>
        <th>Description</th>
    </tr>
    <tr>
        <td>github.api.rate_limit.remaining_requests</td>
        <td><code>{requests}</code></td>
        <td>
            Always reported: <code>github.api.url</code>, <code>github.authentication</code><br/>
            For user based authentication:<code>enduser.id</code><br/>
            For GitHub App based authentication: <code>github.app.id</code>, <code>github.app.owner</code>, 
                <code>github.app.name</code>
        </td>
        <td>Examples:
         <ul>
         <li><code>github.api.url=https://api.github.com</code></li>
         <li><code>github.authentication: anonymous</code> 
            or <code>app.id=1234,app.name="My Jenkins App",app.owner="My Jenkins App"</code> 
            or <code>login=john-doe</code>  or  <code>enduser.id= john-doe</code></li>
         <li><code>github.app.id= 12345, github.app.name="My Jenkins App", github.app.owner= "My Jenkins App"</code></li>
        </ul>
        </td>
        <td>
            When using the GitHub Branch Source plugin, remaining requests for the authenticated GitHub user/app 
            according to the <a href="https://docs.github.com/en/rest/rate-limit">GitHub API Rate Limit</a>
        </td>
    </tr>
    <tr>
        <td>jenkins.scm.event.pool_size</td>
        <td><code>{events}</code></td>
        <td></td>
        <td></td>
        <td>Thread pool size of the SCM Event queue processor</td>
    </tr>
    <tr>
        <td>jenkins.scm.event.active_threads</td>
        <td><code>{threads}</code></td>
        <td></td>
        <td></td>
        <td>Number of active threads of the SCM events thread pool</td>
    </tr>
    <tr>
        <td>jenkins.scm.event.queued_tasks</td>
        <td><code>{tasks}</code></td>
        <td></td>
        <td></td>
        <td>Number of events in the SCM event queue</td>
    </tr>
    <tr>
        <td>jenkins.scm.event.completed_tasks</td>
        <td><code>{tasks}</code></td>
        <td></td>
        <td></td>
        <td>Number of processed SCM events</td>
    </tr>
</table>

## JVM and system metrics

See OpenTelemetry [Semantic Conventions for Runtime Environment Metrics](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/runtime-environment-metrics.md#jvm-metrics).

<table>
    <tr>
        <th>Metric</th>
        <th>Description</th>
        <th>Type</th>
        <th>Attribute Key</th>
        <th>Attribute value</th>
    </tr>
    <tr>
        <td>process.runtime.jvm.buffer.count</td>
        <td>The number of buffers in the pool</td>
        <td>gauge</td>
        <td>pool</td>
        <td>direct, mapped, mapped - 'non-volatile memory'</td>
    </tr>
    <tr>
        <td>process.runtime.jvm.buffer.limit</td>
        <td>Total capacity of the buffers in this pool</td>
        <td> gauge</td>
        <td>pool</td>
        <td>direct, mapped, mapped - 'non-volatile memory'</td>
    </tr>
    <tr>
        <td>process.runtime.jvm.buffer.usage</td>
        <td>Memory that the Java virtual machine is using for this buffer pool</td>
        <td> gauge</td>
        <td>pool</td>
        <td>direct, mapped, mapped - 'non-volatile memory'</td>
    </tr>
    <tr>
        <td>process.runtime.jvm.classes.current_loaded</td>
        <td>Number of classes currently loaded</td>
        <td> gauge</td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>process.runtime.jvm.classes.loaded</td>
        <td>Number of classes loaded since JVM start</td>
        <td> counter</td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>process.runtime.jvm.classes.unloaded</td>
        <td>Number of classes unloaded since JVM start</td>
        <td> counter</td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>process.runtime.jvm.cpu.utilization</td>
        <td>Recent cpu utilization for the process</td>
        <td> gauge</td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>process.runtime.jvm.gc.duration</td>
        <td>Duration of JVM garbage collection actions</td>
        <td> histogram</td>
        <td>action<br/>gc</td>
        <td>end of minor GC...<br/>G1 Young Generation...</td>
    </tr>
    <tr>
        <td>process.runtime.jvm.memory.committed</td>
        <td>Measure of memory committed</td>
        <td> gauge</td>
        <td>pool<br/>type</td>
        <td>CodeHeap 'non-nmethods', CodeHeap 'non-profiled nmethods', CodeHeap
            'profiled nmethods', Compressed Class Space, G1 Eden Space, G1..., Metaspace<br/>heap, non_heap
        </td>
    </tr>
    <tr>
        <td>process.runtime.jvm.memory.init</td>
        <td>Measure of initial memory requested</td>
        <td> gauge</td>
        <td>pool<br/>type</td>
        <td>CodeHeap 'non-nmethods', CodeHeap 'non-profiled nmethods', CodeHeap
            'profiled nmethods', Compressed Class Space, G1 Eden Space, G1..., Metaspace<br/>heap, non_heap
        </td>
    </tr>
    <tr>
        <td>process.runtime.jvm.memory.limit</td>
        <td>Measure of max obtainable memory</td>
        <td> gauge</td>
        <td>pool<br/>type</td>
        <td>CodeHeap 'non-nmethods', CodeHeap 'non-profiled nmethods', CodeHeap
            'profiled nmethods', Compressed Class Space, G1 Eden Space, G1..., Metaspace<br/>heap, non_heap
        </td>
    </tr>
    <tr>
        <td>process.runtime.jvm.memory.usage</td>
        <td>Measure of memory used</td>
        <td> gauge</td>
        <td>pool<br/>type</td>
        <td>CodeHeap 'non-nmethods', CodeHeap 'non-profiled nmethods', CodeHeap
            'profiled nmethods', Compressed Class Space, G1 Eden Space, G1..., Metaspace<br/>heap, non_heap
        </td>
    </tr>
    <tr>
        <td>process.runtime.jvm.memory.usage_after_last_gc</td>
        <td>Measure of memory used after the most recent garbage collection event on this pool</td>
        <td> gauge</td>
        <td>pool<br/>type</td>
        <td>CodeHeap 'non-nmethods', CodeHeap 'G1 Eden Space, G1 Old Gen, G1 Survivor
            Space<br/>heap, non_heap
        </td>
    </tr>
    <tr>
        <td>process.runtime.jvm.system.cpu.load_1m</td>
        <td>Average CPU load of the whole system for the last minute</td>
        <td> gauge</td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>process.runtime.jvm.system.cpu.utilization</td>
        <td>Recent cpu utilization for the whole system</td>
        <td> gauge</td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>process.runtime.jvm.cpu.utilization</td>
        <td>Recent cpu utilization for the process</td>
        <td> gauge</td>
        <td></td>
        <td></td>
    </tr>
    <tr>
        <td>process.runtime.jvm.threads.count</td>
        <td>Number of executing threads</td>
        <td> gauge</td>
        <td>daemon</td>
        <td>true, false</td>
    </tr>
</table>


## Jenkins Security Metrics

| Metrics                          | Unit        | Attribute Key   | Attribute value   | Description            |
|----------------------------------|-------------|-----------------|-------------------|------------------------|
| login                            | ${logins}   |                 |                   | Login count            |
| login_success                    | ${logins}   |                 |                   | Successful login count |
| login_failure                    | ${logins}   |                 |                   | Failed login count     |
