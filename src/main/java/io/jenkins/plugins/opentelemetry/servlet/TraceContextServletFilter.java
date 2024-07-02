package io.jenkins.plugins.opentelemetry.servlet;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Must be a {@link Filter}  rather than a {@link jenkins.util.HttpServletFilter} because it must wrap the invocation of
 * the {@link FilterChain} to ensure that the {@link Span} is correctly associated with the current {@link Context}.
 */
@Extension
public class TraceContextServletFilter implements Filter, OpenTelemetryLifecycleListener {
    private final static Logger logger = Logger.getLogger(StaplerInstrumentationServletFilter.class.getName());

    protected static final Pattern JENKINS_TRIGGER_BUILD_URL_PATTERN = Pattern.compile("^(/[^/]+)?/job/([\\w/-]+)/build(WithParameters)?$");

    final AtomicBoolean w3cTraceContextPropagationEnabled = new AtomicBoolean(false);

    @Inject
    JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest && servletResponse instanceof HttpServletResponse) {
            _doFilter((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse, chain);
        } else {
            chain.doFilter(servletRequest, servletResponse);
        }
    }

    public void _doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (w3cTraceContextPropagationEnabled.get() && isJenkinsRemoteBuildTriggerRequest(request)) {
            Context context = jenkinsControllerOpenTelemetry
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
        return Objects.equals(request.getMethod(), "POST") && JENKINS_TRIGGER_BUILD_URL_PATTERN.matcher(request.getRequestURI()).matches();
    }

    @Override
    public void afterConfiguration(ConfigProperties configProperties) {
        w3cTraceContextPropagationEnabled.set(configProperties.getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_REMOTE_SPAN_ENABLED, false));

        if (!w3cTraceContextPropagationEnabled.get()) {
            logger.log(Level.INFO, () -> "Jenkins trace context propagation disabled on inbound HTTP requests (eg. build triggers). " +
                "To enable it, set the property " + JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_REMOTE_SPAN_ENABLED + " to true.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return TraceContextServletFilter.class.hashCode();
    }

}
