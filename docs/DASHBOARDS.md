# Dashboards

CI/CD dashboards to be imported.

## Elastic

Specific dashboards to be imported in you Kibana instance once the plugin has been configured. Supported version >= `7.12`

There are different ways to import a Kibana dashboard:

* Through the [import API](https://www.elastic.co/guide/en/kibana/current/dashboard-import-api.html)
* Through the [UI](https://www.elastic.co/guide/en/kibana/7.12/managing-saved-objects.html#managing-saved-objects-export-objects)

### Jenkins Overview and Jenkins Provisioning Kibana Dashboards

Import [jenkins-kibana-dashboards.ndjson](./dashboards/elastic/jenkins-kibana-dashboards.ndjson) and you will get something like:

![Jenkins overview](./images/cicd_overview_dashboard.png)

![Jenkins Provisioning](./images/provisioner_dashboard.png)
