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
        <th>Label / attribute key</th>
        <th>Label / attribute value</th>
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
        <th>Label / attribute key</th>
        <th>Label / attribute value</th>
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
        <th>Label / attribute key</th>
        <th>Label / attribute value</th>
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
        <td>Thread pool size of the SCM Event queue processor</td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.scm.event.active_threads</td>
        <td>1</td>
        <td>Number of active threads of the SCM events thread pool</td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.scm.event.queued_tasks</td>
        <td>1</td>
        <td>Number of events in the SCM event queue</td>
        <td></td>
    </tr>
    <tr>
        <td>jenkins.scm.event.completed_tasks</td>
        <td>1</td>
        <td>Number of processed SCM events</td>
        <td></td>
    </tr>
</table>

## JVM and system metrics

<table>
    <tr>
        <th>Metric</th>
        <th>Unit</th>
        <th>Label / attribute key</th>
        <th>Label / attribute value</th>
        <th>Description</th>
    </tr>
   <tr>
        <td>runtime.jvm.gc.time</td>
        <td>ms</td>
        <td>gc</td>
        <td>`G1 Young Generation`, `G1 Old Generation...`</td>
        <td>see [GarbageCollectorMXBean](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.management/com/sun/management/GarbageCollectorMXBean.html)</td>
    </tr>
    <tr>
        <td>runtime.jvm.gc.count</td>
        <td>1</td>
        <td>gc</td>
        <td>`G1 Young Generation`, `G1 Old Generation...`</td>
        <td>see <a href="https://docs.oracle.com/en/java/javase/11/docs/api/jdk.management/com/sun/management/GarbageCollectorMXBean.html">GarbageCollectorMXBean</a></td>
    </tr>
    <tr>
        <td>runtime.jvm.memory.area</td>
        <td>bytes</td>
        <td>type, area</td>
        <td>`used`, `committed`, `max`. &lt;br/&gt; `heap`, `non_heap`</td>
        <td>see <a href="https://docs.oracle.com/en/java/javase/11/docs/api/java.management/java/lang/management/MemoryUsage.html">MemoryUsage</a></td>
    </tr>
    <tr>
        <td>runtime.jvm.memory.pool</td>
        <td>bytes</td>
        <td>type, pool</td>
        <td>`used`, `committed`, `max`. &lt;br/&gt; `PS Eden Space`, `G1 Old Gen...`</td>
        <td>see <a href="https://docs.oracle.com/en/java/javase/11/docs/api/java.management/java/lang/management/MemoryUsage.html">MemoryUsage</a></td>
    </tr>
    <tr>
        <td>system.cpu.load</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>System CPU load. See `com.sun.management.OperatingSystemMXBean.getSystemCpuLoad`</td>
    </tr>
    <tr>
        <td>system.cpu.load.average.1m</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>System CPU load average 1 minute See `java.lang.management.OperatingSystemMXBean.getSystemLoadAverage`</td>
    </tr>
    <tr>
        <td>system.memory.usage</td>
        <td>By</td>
        <td>state</td>
        <td>`used`, `free`</td>
        <td>see `com.sun.management.OperatingSystemMXBean.getTotalPhysicalMemorySize` and `com.sun.management.OperatingSystemMXBean.getFreePhysicalMemorySize`</td>
    </tr>
    <tr>
        <td>system.memory.utilization</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>System memory utilization, see `com.sun.management.OperatingSystemMXBean.getTotalPhysicalMemorySize` and `com.sun.management.OperatingSystemMXBean.getFreePhysicalMemorySize`. Report `0%` if no physical memory is discovered by the JVM.</td>
    </tr>
    <tr>
        <td>system.paging.usage</td>
        <td>By</td>
        <td>state</td>
        <td>`used`, `free`</td>
        <td>see `com.sun.management.OperatingSystemMXBean.getFreeSwapSpaceSize` and `com.sun.management.OperatingSystemMXBean.getTotalSwapSpaceSize`</td>
    </tr>
    <tr>
        <td>system.paging.utilization</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>see `com.sun.management.OperatingSystemMXBean.getFreeSwapSpaceSize` and `com.sun.management.OperatingSystemMXBean.getTotalSwapSpaceSize`. Report `0%` if no swap memory is discovered by the JVM.</td>
    </tr>
    <tr>
        <td>process.cpu.load</td>
        <td>1</td>
        <td></td>
        <td></td>
        <td>Process CPU load. See `com.sun.management.OperatingSystemMXBean.getProcessCpuLoad`</td>
    </tr>
    <tr>
        <td>process.cpu.time</td>
        <td>ns</td>
        <td></td>
        <td></td>
        <td>Process CPU time. See `com.sun.management.OperatingSystemMXBean.getProcessCpuTime`</td>
    </tr>
</table>

## Jenkins Security Metrics

| Metrics                          | Unit  | Label / attribute key | Label / attribute value | Description            |
|----------------------------------|-------|-----------------------|-------------------------|------------------------|
| login                            | 1     |                       |                         | Login count            |
| login_success                    | 1     |                       |                         | Successful login count |
| login_failure                    | 1     |                       |                         | Failed login count     |
