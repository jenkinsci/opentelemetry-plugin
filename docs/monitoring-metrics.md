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

## Jenkins Health Metrics

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
        <td>ci.pipeline.run.active</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Gauge of active jobs</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.launched</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Job launched</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.started</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Job started</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.completed</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Job completed</td>
    </tr>
    <tr>
        <td>ci.pipeline.run.aborted</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Job aborted</td>
    </tr>
    <tr>
        <td>jenkins.queue.waiting</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of tasks in the queue with the status 'buildable' or 'pending' (see <a href="https://javadoc.jenkins.io/hudson/model/Queue.html#getUnblockedItems--">`Queue#getUnblockedItems()`</a>)</td>
    </tr>
    <tr>
        <td>jenkins.queue.blocked</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of blocked tasks in the queue. Note that waiting for an executor to be available is not a reason to be counted as blocked. (see <a href="https://javadoc.jenkins.io/hudson/model/queue/QueueListener.html">`QueueListener#onEnterBlocked() - QueueListener#onLeaveBlocked()`</a>)</td>
    </tr>
    <tr>
        <td>jenkins.queue.buildable</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of tasks in the queue with the status 'buildable' or 'pending' (see <a href="https://javadoc.jenkins.io/hudson/model/Queue.html#getBuildableItems--">`Queue#getBuildableItems()`]</a>)</td>
    </tr>
    <tr>
        <td>jenkins.queue.left</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Total count of tasks that have been processed (see [`QueueListener#onLeft`]()-</td>
    </tr>
    <tr>
        <td>jenkins.queue.time_spent_millis</td>
        <td>ms</td>
        <td></td>
        <td></td>
        <td>Total time spent in queue by the tasks that have been processed (see <a href="https://javadoc.jenkins.io/hudson/model/queue/QueueListener.html#onLeft-hudson.model.Queue.LeftItem-">`QueueListener#onLeft()`</a> and <a href="https://javadoc.jenkins.io/hudson/model/Queue.Item.html#getInQueueSince--">`Item#getInQueueSince()`</a>)</td>
    </tr>
    <tr>
        <td>jenkins.disk.usage.bytes</td>
        <td>By</td>
        <td></td>
        <td></td>
        <td>Disk Usage size</td>
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
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of agents</td>
    </tr>
    <tr>
        <td>jenkins.agents.online</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of online agents</td>
    </tr>
    <tr>
        <td>jenkins.agents.offline</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of offline agents</td>
    </tr>
    <tr>
        <td>jenkins.agents.launch.failure</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of failed launched agents</td>
    </tr>
    <tr>
        <td>jenkins.cloud.agents.completed</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of provisioned cloud agents</td>
    </tr>
    <tr>
        <td>jenkins.cloud.agents.launch.failure</td>
        <td>1</td>
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
        <td>1</td>
        <td>
            Always reported: github.api.url, github.authentication<br/>
            For user based authentication:, enduser.id<br/>
            For GitHub App based authentication: github.app.id, github.app.owner, github.app.name
        </td>
        <td>Examples:
         <ul>
         <li>github.api.url: `https://api.github.com`</li>
         <li>github.authentication: `anonymous` or `app:id=1234,app.name="My Jenkins App",app.owner="My Jenkins App"` or `login:john-doe` 
         enduser.id: `john-doe`</li>
         <li>github.app.id: `12345`, github.app.name: `My Jenkins App`, github.app.owner: `My Jenkins App`</li>
        </ul>
        </td>
        <td>When using the GitHub Branch Source plugin, remaining requests for the authenticated GitHub user/app according to the <a href="https://docs.github.com/en/rest/rate-limit">GitHub API Rate Limit</a></td>
    </tr>
    <tr>
        <td>jenkins.scm.event.pool_size</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Thread pool size of the SCM Event queue processor</td>
    </tr>
    <tr>
        <td>jenkins.scm.event.active_threads</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of active threads of the SCM events thread pool</td>
    </tr>
    <tr>
        <td>jenkins.scm.event.queued_tasks</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of events in the SCM event queue</td>
    </tr>
    <tr>
        <td>jenkins.scm.event.completed_tasks</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Number of processed SCM events</td>
    </tr>
</table>

## JVM and system metrics

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
        <td> gauge</td>
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
        <td>process.runtime.jvm.classes.current.loaded</td>
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

| Metrics                          | Unit  | Attribute Key | Attribute value | Description            |
|----------------------------------|-------|-----------------------|-------------------------|------------------------|
| login                            | 1     |                       |                         | Login count            |
| login_success                    | 1     |                       |                         | Successful login count |
| login_failure                    | 1     |                       |                         | Failed login count     |
