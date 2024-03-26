package io.jenkins.plugins.opentelemetry.servlet;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class TraceContextServletFilter implements Filter {
    private final static Logger logger = Logger.getLogger(StaplerInstrumentationServletFilter.class.getName());

    protected static final Pattern JENKINS_TRIGGER_BUILD_URL_PATTERN = Pattern.compile("^(/[^/]+)?/job/([\\w/-]+)/build(WithParameters)?$");

    private JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest && servletResponse instanceof HttpServletResponse) {
            _doFilter((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse, chain);
        } else {
            chain.doFilter(servletRequest, servletResponse);
        }
    }

    public void _doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (isW3cTraceContextPropagationEnabled() && isJenkinsRemoteBuildTriggerRequest(request)) {
            Context context = getJenkinsControllerOpenTelemetry()
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
        return Objects.equals(request.getMethod(),"POST") && JENKINS_TRIGGER_BUILD_URL_PATTERN.matcher(request.getRequestURI()).matches();
    }

    private boolean isW3cTraceContextPropagationEnabled() {
        return getJenkinsControllerOpenTelemetry().getConfig()
            .getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_REMOTE_SPAN_ENABLED, false);
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

    protected JenkinsControllerOpenTelemetry getJenkinsControllerOpenTelemetry() {
        if (this.jenkinsControllerOpenTelemetry == null) {
            this.jenkinsControllerOpenTelemetry = JenkinsControllerOpenTelemetry.get();
        }
        return this.jenkinsControllerOpenTelemetry;
    }
}
