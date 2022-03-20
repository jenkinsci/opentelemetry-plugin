# Jenkins Security Observability

The Jenkins OpenTelemetry Plugin captures key security events, metrics, and pieces of information.

## Jenkins Security Logs

### Log In Event

Successful and failed log in events are captured as structured log messages:

* Attributes
  * `enduser.id`: user identifier, the value of `hudson.model.User.getId()` if available or the username passed in `j.s.SecurityListener.loggedIn(username)` / `j.s.SecurityListener.failedToLogIn(username)`
  * `event.type`: `login`
  * `event.outcome`: `Success` or `Failure`
  * `net.peer.ip`: `javax.servlet.ServletRequest.getRemoteAddr()`
  * `net.peer.port`: `javax.servlet.ServletRequest.getRemotePort()`
* Body
   * Example: "Successful login of user 'admin'"

## Jenkins Security Metrics

| Metrics                          | Unit  | Label key  | Label Value       | Description            |
|----------------------------------|-------|------------|-------------------|------------------------|
| login                            | 1     |            |                   | Login count            |
| login_success                    | 1     |            |                   | Successful login count |
| login_failure                    | 1     |            |                   | Failed login count     |

## Security Detail in Jenkins HTTP Traces

The traces of all authenticated Jenkins HTTP requests are enriched with the attribute `enduser.id`.