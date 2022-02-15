/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.servlet;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class OpenTelemetryServletFilter implements Filter {
    private final Tracer tracer;

    public OpenTelemetryServletFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        if (servletRequest instanceof HttpServletRequest) {
            _doFilter((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse, filterChain);
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    public void _doFilter(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        if (servletRequest.getPathInfo().startsWith("/job/")) {
            // e.g /job/my-war/job/master/lastBuild/console
            // e.g /job/my-war/job/master/2/console
            ParsedJobUrl parsedJobUrl = parseJobUrl(servletRequest.getPathInfo());
            SpanBuilder spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + parsedJobUrl.urlPattern)
                .setAttribute("http.method", servletRequest.getMethod())
                .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, parsedJobUrl.jobName);
            if (parsedJobUrl.runNumber != null) {
                spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, parsedJobUrl.runNumber);
            }
            Span span = spanBuilder
                .startSpan();
            try (Scope scope = span.makeCurrent()) {
                filterChain.doFilter(servletRequest, servletResponse);
                span.setAttribute("response.status", servletResponse.getStatus());
            } finally {
                span.end();
            }
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    ParsedJobUrl parseJobUrl(String pathInfo) {
        // e.g /job/my-war/job/master/lastBuild/console
        // e.g /job/my-war/job/master/2/console

        int lastJobIdx = pathInfo.lastIndexOf("/job/") + "/job/".length();
        int endOfLastJobToken = pathInfo.indexOf('/', lastJobIdx);
        String urlPattern;
        String jobNameUri;
        Long runNumber;
        if (endOfLastJobToken == -1) {
            urlPattern = "/job/{job.fullName}";
            jobNameUri = pathInfo;
            runNumber = null;
        } else {
            jobNameUri = pathInfo.substring(0, endOfLastJobToken);

            int endOfRunNumberToken = pathInfo.indexOf('/', endOfLastJobToken + 1);

            if (endOfRunNumberToken == -1) {
                endOfRunNumberToken = pathInfo.length();
            }
            String runNumberAsString = pathInfo.substring(endOfLastJobToken + 1, endOfRunNumberToken);
            if ("lastBuild".equals(runNumberAsString)) {
                runNumber = null;
                urlPattern = "/job/{job.fullName}/lastBuild" + pathInfo.substring(endOfRunNumberToken);
            } else if ("".equals(runNumberAsString)) {
                runNumber = null;
                urlPattern = "/job/{job.fullName}/" + pathInfo.substring(endOfRunNumberToken);
            } else if (StringUtils.isNumeric(runNumberAsString)) {
                runNumber = Long.parseLong(runNumberAsString);
                urlPattern = "/job/{job.fullName}/{run.number}" + pathInfo.substring(endOfRunNumberToken);
            } else {
                runNumber = null;
                urlPattern = "/job/{job.fullName}" + pathInfo.substring(endOfLastJobToken);
            }
        }


        String jobName = Arrays.stream(jobNameUri.split("/"))
            .filter(Objects::nonNull)
            .filter(token -> !token.isEmpty() && !Objects.equals("job", token))
            .collect(Collectors.joining("/"));
        return new ParsedJobUrl(jobName, runNumber, urlPattern);
    }

    public static class ParsedJobUrl {
        public ParsedJobUrl(String jobName, @Nullable Long runNumber, String urlPattern) {
            this.jobName = jobName;
            this.urlPattern = urlPattern;
            this.runNumber = runNumber;
        }

        final String jobName;
        @Nullable
        final Long runNumber;
        final String urlPattern;

        @Override
        public String toString() {
            return "ParsedJobUrl{" +
                "jobName='" + jobName + '\'' +
                "runNumber='" + runNumber + '\'' +
                ", uri='" + urlPattern + '\'' +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ParsedJobUrl that = (ParsedJobUrl) o;
            return Objects.equals(jobName, that.jobName) && Objects.equals(runNumber, that.runNumber) && Objects.equals(urlPattern, that.urlPattern);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jobName, runNumber, urlPattern);
        }
    }

    @Override
    public void destroy() {

    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return OpenTelemetryServletFilter.class.hashCode();
    }
}
