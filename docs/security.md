# Jenkins Security Observability

The Jenkins OpenTelemetry Plugin captures key security events, metrics, and pieces of information.

## Jenkins Security Logs

### Log In Event

Successful and failed log in events are captured as structured log messages:

* Login success
  * Attributes
    * `enduser.id`: user identifier, the value of `hudson.model.User.getId()` if available or the username passed in `j.s.SecurityListener.loggedIn(username)`
    * `event.category`: `authentication`
    * `event.action`: `user_login`
    * `event.outcome`: `success`
    * `net.peer.ip`: `javax.servlet.ServletRequest.getRemoteAddr()`
  * Body
     * Example: "Successful login of user 'admin' from 127.0.0.1"
* Login failure
    * Attributes
        * `enduser.id`: username passed in `j.s.SecurityListener.failedToLogIn(username)`
        * `event.category`: `authentication`
        * `event.action`: `user_login`
        * `event.outcome`: `failure`
    * Body
        * Example: "Successful login of user 'admin'"

Known limitations:
* Some Jenkins authentication plugins such as the [Jenkins GitHub Authentication Plugin](https://plugins.jenkins.io/github-oauth/) won't capture the "Successful login" or the "Failed login" event because they don't invoke the `SecurityListener.loggedIn(username)` or the `SecurityListener.failedLoggedIn(username)` APIs,
* The remote IP address is not captured on the "Failed login" event due to restrictions on Jenkins APIs.

## Jenkins Security Metrics

| Metrics                          | Unit  | Label key  | Label Value       | Description            |
|----------------------------------|-------|------------|-------------------|------------------------|
| login                            | 1     |            |                   | Login count            |
| login_success                    | 1     |            |                   | Successful login count |
| login_failure                    | 1     |            |                   | Failed login count     |

## Security Detail in Jenkins HTTP Traces

The traces of all authenticated Jenkins HTTP requests are enriched with the attribute `enduser.id`.