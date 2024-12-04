

# `http.server.request.duration`

## HTTP Requests per second


### HTTP Requests per second

```promql
sum by(http_request_method, http_route, http_response_status_code) (rate(http_server_request_duration_seconds_count[$__rate_interval]) * 60)
```
Grafana Panel legend: `{{http_request_method}} {{http_route}} {{http_response_status_code}}`

### HTTP Error Rate

```promql
sum without (http_response_status_code, error_type) (rate(http_server_request_duration_seconds_count{job="$job", http_request_method="$http_request_method", http_route="$http_route", http_response_status_code=~"5.."}[$__rate_interval])) / sum without (http_response_status_code, error_type) (rate(http_server_request_duration_seconds_count{job="$job", http_request_method="$http_request_method", http_route="$http_route"}[$__rate_interval])) * 100
```

```promql
sum without (http_response_status_code, error_type) (
    rate(
        http_server_request_duration_seconds_count{job="$job", http_request_method="$http_request_method", http_route="$http_route", http_response_status_code=~"5.."}[$__rate_interval]
    )
) / 
sum without (http_response_status_code, error_type) (
    rate(
        http_server_request_duration_seconds_count{job="$job", http_request_method="$http_request_method", http_route="$http_route"}[$__rate_interval]
    )
) * 100
```

### Top failing HTTP requests

```promql
topk(5, sum by(http_request_method, http_route) (rate(http_server_request_duration_seconds_count{http_response_status_code=~"5.."}[$__rate_interval])))
```

Grafana panel legend: `{{http_request_method}} {{http_route}}`

