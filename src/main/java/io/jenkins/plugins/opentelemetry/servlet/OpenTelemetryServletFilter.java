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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.StringTokenizer;
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

        String pathInfo = servletRequest.getPathInfo();
        List<String> pathInfoTokens = Collections.list(new StringTokenizer(pathInfo, "/")).stream()
            .map(token -> (String) token)
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toList());


        if (pathInfoTokens.isEmpty()) {
            pathInfoTokens = Collections.singletonList("");
        }


        String rootPath = pathInfoTokens.get(0);

        if (rootPath.equals("static") || rootPath.equals("adjuncts") || rootPath.equals("scripts")) {
            // skip
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            SpanBuilder spanBuilder;
            if (rootPath.equals("job")) {
                // e.g /job/my-war/job/master/lastBuild/console
                // e.g /job/my-war/job/master/2/console
                ParsedJobUrl parsedJobUrl = parseJobUrl(pathInfoTokens.subList(1, pathInfoTokens.size()));
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + parsedJobUrl.urlPattern)
                    .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, parsedJobUrl.jobName);
                if (parsedJobUrl.runNumber != null) {
                    spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, parsedJobUrl.runNumber);
                }
            } else if (rootPath.equals("descriptorByName")) {
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/descriptorByName/");
                spanBuilder.setAttribute("descriptor", pathInfo.substring("/descriptorByName/".length()));
            } else if (rootPath.equals("fingerprint")) {
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/fingerprint/");
                spanBuilder.setAttribute("fingerprint", pathInfo.substring("/fingerprint/".length()));
            } else {
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + pathInfo);
            }
            Span span = spanBuilder
                .setAttribute("http.method", servletRequest.getMethod())
                .startSpan();
            try (Scope scope = span.makeCurrent()) {
                filterChain.doFilter(servletRequest, servletResponse);
                span.setAttribute("response.status", servletResponse.getStatus());
            } finally {
                span.end();
            }
        }
    }

    ParsedJobUrl parseJobUrl(List<String> pathInfoTokens) {
        // e.g /job/my-war/job/master/lastBuild/console
        // e.g /job/my-war/job/master/2/console
        // TODO http://localhost:8080/jenkins/job/my-war/job/master/113/execution/node/3/
        // TODO http://localhost:8080/jenkins/job/my-war/job/master/114/artifact/target/my-war-1.0-SNAPSHOT.war

        if (pathInfoTokens.isEmpty()) {
            throw new IllegalArgumentException("Job URL cannot be empty");
        }

        Iterator<String> pathInfoTokensIt = pathInfoTokens.listIterator();
        String firstToken = pathInfoTokens.get(0);
        if (!"job".equals(firstToken)) {
            throw new IllegalArgumentException("Job URL doesn't start with '/job'" + pathInfoTokens.stream().collect(Collectors.joining("/")));
        }

        List<String> jobName = new ArrayList<>(5);
        List<String> jobUrlPattern = new ArrayList<>(Arrays.asList("job", "{job.fullName}"));
        int idx = 0;
        // EXTRACT JOB NAME
        while (pathInfoTokensIt.hasNext()) {
            String token = pathInfoTokensIt.next();
            String previousToken = idx == 0 ? null : pathInfoTokens.get(idx -1);
            String nextToken = pathInfoTokensIt.hasNext() ? pathInfoTokens.get(idx +1) : null;
            if ("job".equals(previousToken)) {
                jobName.add(token);
                if ("job".equals(nextToken)) {
                    // continue parsing job name
                } else {
                    // end of job name
                    break;
                }
            } else if ("job".equals(token)) {
                // skip
            } else {
                throw new IllegalStateException("Unexpected token '" + token + "' with previousToken '" + previousToken + "' and nextToken '" + nextToken + "' in " + pathInfoTokens.stream().collect(Collectors.joining("/")));
            }
            idx++;
        }
        // EXTRACT TRAILING PATH
        Long runNumber;
        if (pathInfoTokensIt.hasNext()) {
            String token = pathInfoTokensIt.next();
            if ("lastBuild".equals(token)) {
                runNumber = null;
                jobUrlPattern.add("lastBuild");
            } else if (StringUtils.isNumeric(token)) {
                runNumber = Long.parseLong(token);
                jobUrlPattern.add("{run.number}");
                pathInfoTokensIt.forEachRemaining(jobUrlPattern::add);
            } else {
                jobUrlPattern.add(token);
                runNumber = null;
            }
            pathInfoTokensIt.forEachRemaining(jobUrlPattern::add);
        } else {
            runNumber = null;
        }

        return new ParsedJobUrl(jobName.stream().collect(Collectors.joining("/")), runNumber, "/" + jobUrlPattern.stream().collect(Collectors.joining("/")));
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
