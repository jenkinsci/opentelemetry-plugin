package io.jenkins.plugins.opentelemetry.servlet;

import com.google.common.collect.Iterators;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.eclipse.jgit.util.HttpSupport.METHOD_POST;

public class RemoteSpanServletFilter implements Filter {
    private final static Logger logger = Logger.getLogger(OpenTelemetryServletFilter.class.getName());

    private static final String REGEX_PATTERN = "^(/[^/]+)?/job/([\\w/-]+)/build(WithParameters)?$";
    private static final Pattern PATTERN = Pattern.compile(REGEX_PATTERN);

    public static boolean isJenkinsRemoteBuildURL(String urlPath) {
        Matcher matcher = PATTERN.matcher(urlPath);
        return matcher.matches();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {

        if (isRemoteSpanEnabled() && isRemoteBuildRequest(servletRequest)) {
            traceParentContext(servletRequest, servletResponse, chain);
        } else {
            chain.doFilter(servletRequest, servletResponse);
        }


    }

    private boolean isRemoteBuildRequest(ServletRequest servletRequest) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        return httpServletRequest.getMethod().equals(METHOD_POST) && isJenkinsRemoteBuildURL(httpServletRequest.getRequestURI());

    }

    private boolean isRemoteSpanEnabled() {
        return OpenTelemetrySdkProvider.get().getConfig().getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_REMOTE_SPAN_ENABLED,false);
    }
    private static void traceParentContext(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        Context parentContext = OpenTelemetrySdkProvider.get().getPropagators().getTextMapPropagator().extract(Context.current(), (HttpServletRequest) servletRequest, new TextMapGetter<HttpServletRequest>() {
            @Override
            public Iterable<String> keys(HttpServletRequest carrier) {
                return () -> Optional.ofNullable(carrier)
                    .map(HttpServletRequest::getHeaderNames)
                    .map(headers -> Iterators.forEnumeration(headers))
                    .orElseGet(() -> Iterators.forEnumeration(Collections.emptyEnumeration()));
            }


            @Override
            public String get(@javax.annotation.Nullable HttpServletRequest carrier, String key) {
                return Optional.ofNullable(carrier)
                    .map(c -> c.getHeader(key))
                    .orElse(null);
            }
        });
        try (Scope scope = parentContext.makeCurrent()) {
            chain.doFilter(servletRequest, servletResponse);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return RemoteSpanServletFilter.class.hashCode();
    }
}
