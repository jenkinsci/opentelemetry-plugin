package io.jenkins.plugins.opentelemetry.servlet;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.ConfigurationKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Must be a {@link Filter}  rather than a {@link jenkins.util.HttpServletFilter} because it must wrap the invocation of
 * the {@link FilterChain} to ensure that the {@link Span} is correctly associated with the current {@link Context}.
 */
@Extension
public class TraceContextServletFilter implements Filter, OpenTelemetryLifecycleListener {
    private static final Logger logger = Logger.getLogger(StaplerInstrumentationServletFilter.class.getName());

    protected static final Pattern JENKINS_TRIGGER_BUILD_URL_PATTERN =
            Pattern.compile("^(/[^/]+)?/job/([\\w/-]+)/build(WithParameters)?$");

    final AtomicBoolean w3cTraceContextPropagationEnabled = new AtomicBoolean(false);

    @Inject
    ReconfigurableOpenTelemetry openTelemetry;

    /**
     * Delegates to HTTP-specific filtering when request and response are HTTP types.
     *
     * @param servletRequest incoming servlet request
     * @param servletResponse outgoing servlet response
     * @param chain filter chain
     * @throws IOException on I/O failures
     * @throws ServletException on servlet processing failures
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest && servletResponse instanceof HttpServletResponse) {
            _doFilter((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse, chain);
        } else {
            chain.doFilter(servletRequest, servletResponse);
        }
    }

    /**
     * Extracts inbound W3C trace context for remote build-trigger requests when enabled.
     *
     * @param request incoming HTTP request
     * @param response outgoing HTTP response
     * @param chain filter chain
     * @throws IOException on I/O failures
     * @throws ServletException on servlet processing failures
     */
    public void _doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (w3cTraceContextPropagationEnabled.get() && isJenkinsRemoteBuildTriggerRequest(request)) {
            Context context = openTelemetry
                    .getPropagators()
                    .getTextMapPropagator()
                    .extract(Context.current(), request, new OtelUtils.HttpServletRequestTextMapGetter());
            try (Scope scope = context.makeCurrent()) {
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * @return {@code true} if the given request is an HTTP request to trigger a Jenkins build
     */
    private boolean isJenkinsRemoteBuildTriggerRequest(@NonNull HttpServletRequest request) {
        return Objects.equals(request.getMethod(), "POST")
                && JENKINS_TRIGGER_BUILD_URL_PATTERN
                        .matcher(request.getRequestURI())
                        .matches();
    }

    @Override
    public void afterConfiguration(ConfigProperties configProperties) {
        w3cTraceContextPropagationEnabled.set(configProperties.getBoolean(
                ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_REMOTE_SPAN_ENABLED.asProperty(), false));

        if (!w3cTraceContextPropagationEnabled.get()) {
            logger.log(
                    Level.INFO,
                    () -> "Jenkins trace context propagation disabled on inbound HTTP requests (eg. build triggers). "
                            + "To enable it, set the property "
                            + ConfigurationKey.OTEL_INSTRUMENTATION_JENKINS_REMOTE_SPAN_ENABLED.asProperty()
                            + " to true.");
        }
    }

    /**
     * Compares by concrete type because this filter has no mutable identity fields.
     *
     * @param o object to compare
     * @return {@code true} when both instances are the same filter type
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    /**
     * Returns a stable hash code for this stateless filter type.
     *
     * @return hash code for this class
     */
    @Override
    public int hashCode() {
        return TraceContextServletFilter.class.hashCode();
    }
}
