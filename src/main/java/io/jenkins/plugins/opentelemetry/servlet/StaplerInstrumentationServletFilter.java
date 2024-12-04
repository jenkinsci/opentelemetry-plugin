/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.servlet;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.User;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import io.opentelemetry.semconv.incubating.UserIncubatingAttributes;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Instrumentation of the Stapler MVC framework.
 * Must be a {@link Filter}  rather than a {@link jenkins.util.HttpServletFilter} because it must wrap the invocation of the {@link FilterChain} to create a {@link Span}.
 * TODO find a smarter way to instrument each HTTP request path. It should rely on instrumenting the Stapler framework
 * TODO adopt <a href="https://javadoc.jenkins.io/component/stapler/org/kohsuke/stapler/StaplerRequest2.html#getAncestors()">StaplerRequest2.html#getAncestors()</a>
 */
@Extension
public class StaplerInstrumentationServletFilter implements Filter, OpenTelemetryLifecycleListener {
    private final static Logger logger = Logger.getLogger(StaplerInstrumentationServletFilter.class.getName());
    final AtomicBoolean enabled = new AtomicBoolean(false);
    List<String> capturedRequestParameters;
    Tracer tracer;
    Meter meter;
    OperationListener httpServerMetrics;

    @PostConstruct
    public void postConstruct() {
        httpServerMetrics = HttpServerMetrics.get().create(meter);
    }

    @Override
    public void afterConfiguration(ConfigProperties configProperties) {
        enabled.set(configProperties.getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_WEB_ENABLED, true));

        logger.log(Level.FINE, () -> "Jenkins Web instrumentation enabled: " + enabled.get() + ". To change config, use the property " +
            JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_WEB_ENABLED + ".");

        capturedRequestParameters = configProperties.getList(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_SERVLET_CAPTURE_REQUEST_PARAMETERS, Collections.emptyList());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        if (enabled.get() && servletRequest instanceof HttpServletRequest) {
            _doFilter((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse, filterChain);
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    public void _doFilter(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        // Attributes common to Http Server span and metric
        AttributesBuilder httpServerMetricOnStartAttributesBuilder = Attributes.builder()
            .put(HttpAttributes.HTTP_REQUEST_METHOD, servletRequest.getMethod())
            .put(UrlAttributes.URL_SCHEME, servletRequest.getScheme())
            .put(ServerAttributes.SERVER_ADDRESS, servletRequest.getServerName())
            .put(ServerAttributes.SERVER_PORT, (long) servletRequest.getServerPort());

        String sanitizedUrl = servletRequest.getScheme() + "://" + servletRequest.getServerName() + ":" + servletRequest.getServerPort() + servletRequest.getRequestURI() + (servletRequest.getQueryString() != null ? "?" + servletRequest.getQueryString() : "");
        Thread currentThread = Thread.currentThread();
        AttributesBuilder httpServerSpanAttributesBuilder = Attributes.builder()
            .putAll(httpServerMetricOnStartAttributesBuilder.build())
            .put(ThreadIncubatingAttributes.THREAD_NAME, currentThread.getName())
            .put(ThreadIncubatingAttributes.THREAD_ID, currentThread.getId())
            .put(ClientAttributes.CLIENT_ADDRESS, servletRequest.getRemoteAddr())
            .put(ClientAttributes.CLIENT_PORT, (long) servletRequest.getRemotePort())
            .put(UrlAttributes.URL_FULL, sanitizedUrl)
            .put(UserAgentAttributes.USER_AGENT_ORIGINAL, servletRequest.getHeader("User-Agent"));
        Optional.ofNullable(User.current()).ifPresent(user -> httpServerSpanAttributesBuilder.put(UserIncubatingAttributes.USER_ID, user.getId()));

        Context httpServerDurationMetricContext = httpServerMetrics.onStart(Context.current(), httpServerMetricOnStartAttributesBuilder.build(), System.nanoTime());

        AttributesBuilder httpServerMetricOnEndAttributesBuilder = Attributes.builder();
        try {

            List<String> pathInfoTokens = Collections.list(new StringTokenizer(servletRequest.getPathInfo(), "/")).stream()
                .map(token -> (String) token)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());

            if (pathInfoTokens.isEmpty()) {
                pathInfoTokens = Collections.singletonList("");
            }

            String rootPath = pathInfoTokens.get(0);
            String httpRoute;

            boolean skipSpan = false;
            try {
                switch (rootPath) {
                    case "job" -> {
                        // e.g /job/my-war/job/master/lastBuild/console
                        // e.g /job/my-war/job/master/2/console
                        ParsedJobUrl parsedJobUrl = parseJobUrl(pathInfoTokens);
                        httpRoute = parsedJobUrl.urlPattern;
                        Optional.ofNullable(parsedJobUrl.jobName).ifPresent(jobName -> httpServerSpanAttributesBuilder.put(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobName));
                        Optional.ofNullable(parsedJobUrl.runNumber).ifPresent(runNumber -> httpServerSpanAttributesBuilder.put(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, runNumber));
                    }
                    case "blue" -> {
                        if (pathInfoTokens.size() == 1) {
                            httpRoute = "/blue/";
                        } else if ("rest".equals(pathInfoTokens.get(1))) {
                            if (pathInfoTokens.size() == 2) {
                                httpRoute = "/blue/rest/";
                            } else if ("organizations".equals(pathInfoTokens.get(2)) && pathInfoTokens.size() > 7) {
                                // eg /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/blueTestSummary/
                                ParsedJobUrl parsedBlueOceanPipelineUrl = parseBlueOceanRestPipelineUrl(pathInfoTokens);
                                httpRoute = parsedBlueOceanPipelineUrl.urlPattern;
                                Optional.ofNullable(parsedBlueOceanPipelineUrl.jobName).ifPresent(jobName -> httpServerSpanAttributesBuilder.put(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobName));
                                Optional.ofNullable(parsedBlueOceanPipelineUrl.runNumber).ifPresent(runNumber -> httpServerSpanAttributesBuilder.put(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, runNumber));
                            } else if ("classes".equals(pathInfoTokens.get(2)) && pathInfoTokens.size() > 3) {
                                // eg /blue/rest/classes/io.jenkins.blueocean.rest.impl.pipeline.PipelineRunImpl/
                                String blueOceanClass = pathInfoTokens.get(3);
                                httpRoute = "/blue/rest/classes/:blueOceanClass";
                                httpServerSpanAttributesBuilder.put("blueOceanClass", blueOceanClass);
                            } else {
                                // eg /blue/rest/i18n/blueocean-personalization/1.25.2/jenkins.plugins.blueocean.personalization.Messages/en-US
                                httpRoute = "/blue/rest/" + pathInfoTokens.get(2) + "/*";
                            }
                        } else {
                            httpRoute = "/blue/" + pathInfoTokens.get(1) + "/*";
                        }
                    }
                    case "administrativeMonitor" -> {
                        // eg GET /administrativeMonitor/hudson.diagnosis.ReverseProxySetupMonitor/testForReverseProxySetup/http://localhost:8080/jenkins/manage/
                        httpRoute = "/administrativeMonitor/:administrativeMonitor/*";
                        if (pathInfoTokens.size() > 1) {
                            httpServerSpanAttributesBuilder.put("administrativeMonitor", pathInfoTokens.get(1));
                        }
                    }
                    case "asynchPeople" -> {
                        httpRoute = "/asynchPeople";
                    }
                    case "computer" -> {
                        // /computer/(master)/
                        httpRoute = "/computer/:computer/*";
                        // TODO add details
                    }
                    case "credentials" -> {
                        // eg /credentials/store/system/domain/_/
                        httpRoute = "/credentials/store/:store/domain/:domain/*";
                        // TODO add details
                    }
                    case "descriptorByName" -> {
                        httpRoute = "/descriptorByName/:descriptor/*";
                        if (pathInfoTokens.size() > 1) {
                            httpServerSpanAttributesBuilder.put("descriptor", pathInfoTokens.get(1));
                        }
                    }
                    case "extensionList" -> {
                        // eg /extensionList/hudson.diagnosis.MemoryUsageMonitor/0/heap/graph
                        httpRoute = "/extensionList/:extension/*";
                        if (pathInfoTokens.size() > 1) {
                            httpServerSpanAttributesBuilder.put("extension", pathInfoTokens.get(1));
                        }
                    }
                    case "fingerprint" -> {
                        httpRoute = "/fingerprint/:fingerprint";
                        httpServerSpanAttributesBuilder.put("fingerprint", servletRequest.getPathInfo().substring("/fingerprint/".length()));
                        if (pathInfoTokens.size() > 1) {
                            httpServerSpanAttributesBuilder.put("fingerprint", pathInfoTokens.get(1));
                        }
                    }
                    case "user" -> {
                        //eg /user/cyrille.leclerc/ /user/cyrille.leclerc/configure /user/cyrille.leclerc/my-views/view/all/
                        httpRoute = "/user/:user/*";
                        if (pathInfoTokens.size() > 1) {
                            httpServerSpanAttributesBuilder.put("user", pathInfoTokens.get(1));
                        }
                    }
                    default -> {
                        // "static", "adjuncts", "scripts", "plugin", "images", "sse-gateway"
                        // e.g /$stapler/bound/ec328aeb-26be-43da-94a3-59f2d683131c/news
                        httpRoute = "/*";
                        skipSpan = true;
                    }
                }
            } catch (RuntimeException e) {
                logger.log(Level.INFO, () -> "Exception processing URL " + servletRequest.getPathInfo() + ", skip instrumentation with tracing: " + e);
                httpRoute = "/##error-processing-url-to-extract-http-route##";
            }

            httpServerSpanAttributesBuilder.put(HttpAttributes.HTTP_ROUTE, httpRoute);
            httpServerMetricOnEndAttributesBuilder.put(HttpAttributes.HTTP_ROUTE, httpRoute);
            capturedRequestParameters.forEach(
                parameterName ->
                    Optional.ofNullable(servletRequest.getParameter(parameterName))
                        .ifPresent(value -> httpServerSpanAttributesBuilder.put("http.request.parameter." + parameterName, value)));

            Span span = skipSpan ? Span.getInvalid() :  tracer.spanBuilder(servletRequest.getMethod() + " " + httpRoute)
                .setAllAttributes(httpServerSpanAttributesBuilder.build())
                .setSpanKind(SpanKind.SERVER).startSpan();
            try (Scope scope = span.makeCurrent()) {
                filterChain.doFilter(servletRequest, servletResponse);
            } catch (IOException | ServletException | RuntimeException e) {
                if (servletResponse.getStatus() < 500) {
                    servletResponse.setStatus(500);
                }
                span.recordException(e);
                httpServerMetricOnEndAttributesBuilder.put(ErrorAttributes.ERROR_TYPE, e.getClass().getName());
                throw e;
            } finally {
                span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, servletResponse.getStatus());
                httpServerMetricOnEndAttributesBuilder.put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, servletResponse.getStatus());
                span.end();
            }
        } finally {
            httpServerMetrics.onEnd(httpServerDurationMetricContext, httpServerMetricOnEndAttributesBuilder.build(), System.nanoTime());
        }
    }

    /**
     * Throws an {@link IllegalArgumentException} if the given {@code pathInfo} doesn't match the given {@code expected} path
     *
     * @param pathInfo
     * @param expected '*' element means not check
     * @throws IllegalArgumentException if not matching
     */
    void checkUrlPathInfoMatch(List<String> pathInfo, List<String> expected) {
        int violationIndex = getUrlPathInfoMatch(pathInfo, expected);
        if (violationIndex >= 0) {
            throw new IllegalArgumentException("Invalid URL path /" + String.join("/", pathInfo) + ", expected: /" + String.join("/", expected) + ". " +
                "Violation on item " + violationIndex + ", " +
                "expected: '" + expected.get(violationIndex) + "', " +
                "actual: '" + (violationIndex < pathInfo.size() ? "<<missing>>" : pathInfo.get(violationIndex)) + "'");
        }
    }

    /**
     * Return {@code false} if the given {@code pathInfo} doesn't match the given {@code expected} path
     *
     * @param pathInfo
     * @param expected '*' element means not check
     */
    boolean isUrlPathInfoMatch(List<String> pathInfo, String... expected) {
        return isUrlPathInfoMatch(pathInfo, Arrays.asList(expected));
    }

    /**
     * Return {@code false} if the given {@code pathInfo} doesn't match the given {@code expected} path
     *
     * @param pathInfo
     * @param expected expected uri segment. Segments starting with ':' or '*' means wildcard
     */
    boolean isUrlPathInfoMatch(List<String> pathInfo, List<String> expected) {
        int violationIndex = getUrlPathInfoMatch(pathInfo, expected);
        return violationIndex == -1;
    }

    /**
     * @param pathInfo
     * @param expected expected uri segment. Segments starting with ':' or '*' means wildcard
     */
    private int getUrlPathInfoMatch(List<String> pathInfo, List<String> expected) {
        int violationIndex = -1;

        if (pathInfo.size() < expected.size()) {
            violationIndex = pathInfo.size() + 1;
        }
        for (int i = 0; i < expected.size(); i++) {
            String expectedPathInfo = expected.get(i);
            String actualPathInfo = pathInfo.get(i);
            if (expectedPathInfo.startsWith("*") || expectedPathInfo.startsWith(":")) {
                // no check
            } else if (expectedPathInfo.equals(actualPathInfo)) {
                // it's a match
            } else {
                violationIndex = i;
            }
        }
        return violationIndex;
    }

    ParsedJobUrl parseBlueOceanRestPipelineUrl(List<String> pathInfo) {
        // /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/blueTestSummary/
        // /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/
        // /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/19/log/

        List<String> job;
        Long runNumber;
        String nodeId;
        Integer stepId;
        List<String> jobUrlPattern;

        if (pathInfo.size() > 14 && isUrlPathInfoMatch(pathInfo, "blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber", "nodes", ":node", "steps", ":step", "*")) {
            job = Arrays.asList(pathInfo.get(5), pathInfo.get(7));
            runNumber = Long.parseLong(pathInfo.get(9));
            nodeId = pathInfo.get(11);
            stepId = Integer.parseInt(pathInfo.get(13));
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber", "nodes", ":node", "steps", ":step"));
            if (pathInfo.size() > jobUrlPattern.size() + 1) {
                jobUrlPattern.add("*");
            } else {
                jobUrlPattern.add(pathInfo.get(jobUrlPattern.size()));
            }
        } else if (pathInfo.size() > 10 && isUrlPathInfoMatch(pathInfo, "blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "runs", ":runNumber", "steps", ":step", "*")) {
            // /blue/rest/organizations/jenkins/pipelines/my-war-pipeline/runs/1/steps/5/log/
            job = Collections.singletonList(pathInfo.get(5));
            runNumber = Long.parseLong(pathInfo.get(7));
            nodeId = null;
            stepId = Integer.parseInt(pathInfo.get(9));
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "runs", ":runNumber", "steps", ":step"));
            if (pathInfo.size() > jobUrlPattern.size() + 1) {
                jobUrlPattern.add("*");
            } else {
                jobUrlPattern.add(pathInfo.get(jobUrlPattern.size()));
            }
        } else if (pathInfo.size() > 13 && isUrlPathInfoMatch(pathInfo, "blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber", "nodes", ":node", "steps", "*")) {
            job = Arrays.asList(pathInfo.get(5), pathInfo.get(7));
            runNumber = Long.parseLong(pathInfo.get(9));
            nodeId = pathInfo.get(11);
            stepId = Integer.parseInt(pathInfo.get(13));
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber", "nodes", ":node", "steps"));
            if (pathInfo.size() > jobUrlPattern.size() + 1) {
                jobUrlPattern.add("*");
            } else {
                jobUrlPattern.add(pathInfo.get(jobUrlPattern.size()));
            }
        } else if (pathInfo.size() > 12 && isUrlPathInfoMatch(pathInfo, "blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber", "nodes", ":node", "*")) {
            // /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/
            job = Arrays.asList(pathInfo.get(5), pathInfo.get(7));
            runNumber = Long.parseLong(pathInfo.get(9));
            nodeId = pathInfo.get(11);
            stepId = null;
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber", "nodes", ":node"));
            if (pathInfo.size() > jobUrlPattern.size() + 1) {
                jobUrlPattern.add("*");
            } else {
                jobUrlPattern.add(pathInfo.get(jobUrlPattern.size()));
            }
        } else if (pathInfo.size() > 8 && isUrlPathInfoMatch(pathInfo, "blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "runs", ":runNumber")) {
            // /blue/rest/organizations/jenkins/pipelines/my-war-pipeline/runs/1/steps/ TODO
            // /blue/rest/organizations/jenkins/pipelines/my-war-pipeline/runs/1/blueTestSummary/
            job = Collections.singletonList(pathInfo.get(5));
            runNumber = Long.parseLong(pathInfo.get(7));
            nodeId = null;
            stepId = null;
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "runs", ":runNumber"));
            if (pathInfo.size() > jobUrlPattern.size() + 1) {
                jobUrlPattern.add("*");
            } else {
                jobUrlPattern.add(pathInfo.get(jobUrlPattern.size()));
            }
        } else if (pathInfo.size() > 10 && isUrlPathInfoMatch(pathInfo, "blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber")) {
            job = Arrays.asList(pathInfo.get(5), pathInfo.get(7));
            runNumber = Long.parseLong(pathInfo.get(9));
            nodeId = null;
            stepId = null;
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber"));
            if (pathInfo.size() > jobUrlPattern.size() + 1) {
                jobUrlPattern.add("*");
            } else {
                jobUrlPattern.add(pathInfo.get(jobUrlPattern.size()));
            }
        } else if (pathInfo.size() > 9 && isUrlPathInfoMatch(pathInfo, "blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber")) {
            job = Arrays.asList(pathInfo.get(5), pathInfo.get(7));
            runNumber = Long.parseLong(pathInfo.get(9));
            nodeId = null;
            stepId = null;
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "branches", ":branch", "runs", ":runNumber"));
        } else if (pathInfo.size() > 7 && isUrlPathInfoMatch(pathInfo, "blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "runs", ":runNumber")) {
            // /blue/rest/organizations/jenkins/pipelines/my-war-pipeline/runs/1/
            job = Collections.singletonList(pathInfo.get(5));
            runNumber = Long.parseLong(pathInfo.get(7));
            nodeId = null;
            stepId = null;
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "runs", ":runNumber"));

        } else if (pathInfo.size() > 6 && isUrlPathInfoMatch(pathInfo, "blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "*")) {
            // /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/scm/content
            job = Collections.singletonList(pathInfo.get(5));
            runNumber = null;
            nodeId = null;
            stepId = null;
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "organizations", ":organization", "pipelines", ":pipelineName", "*"));
        } else if (pathInfo.size() > 4 && isUrlPathInfoMatch(pathInfo, "blue", "organizations", ":organization", ":pipelineName", "activity")) {
            // /blue/organizations/jenkins/my-war-pipeline/activity
            job = Collections.singletonList(pathInfo.get(3));
            runNumber = null;
            nodeId = null;
            stepId = null;
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "organizations", ":organization", ":pipelineName", "activity"));
        } else if (pathInfo.size() > 1 && isUrlPathInfoMatch(Arrays.asList("blue", "rest", "*"))) {
            // // /blue/rest/
            job = Collections.emptyList();
            runNumber = null;
            nodeId = null;
            stepId = null;
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "rest", "*"));
        } else {
            job = Collections.emptyList();
            runNumber = null;
            nodeId = null;
            stepId = null;
            jobUrlPattern = new ArrayList<>(Arrays.asList("blue", "*"));
        }

        return new ParsedBlueOceanPipelineJobUrl(job, runNumber, jobUrlPattern, nodeId, stepId);
    }

    ParsedJobUrl parseJobUrl(List<String> pathInfo) {
        // e.g /job/my-war/job/master/lastBuild/console
        // e.g /job/my-war/job/master/2/console
        // TODO /job/my-war/job/master/113/execution/node/3/
        // TODO /job/my-war/job/master/114/artifact/target/my-war-1.0-SNAPSHOT.war
        // TODO /job/my-war/descriptorByName/org.jenkinsci.plugins.github_branch_source.OriginPullRequestDiscoveryTrait/fillStrategyIdItems

        if (pathInfo.isEmpty()) {
            throw new IllegalArgumentException("Job URL cannot be empty");
        }

        Iterator<String> pathInfoTokensIt = pathInfo.listIterator();
        String firstToken = pathInfo.get(0);
        if (!"job".equals(firstToken)) {
            throw new IllegalArgumentException("Job URL.pathInfo doesn't start with '/job': " + String.join("/", pathInfo));
        }

        List<String> jobName = new ArrayList<>(5);
        List<String> jobUrlPattern = new ArrayList<>(Arrays.asList("job", ":jobFullName"));
        int idx = 0;
        // EXTRACT JOB NAME
        while (pathInfoTokensIt.hasNext()) {
            String token = pathInfoTokensIt.next();
            String previousToken = idx == 0 ? null : pathInfo.get(idx - 1);
            String nextToken = pathInfoTokensIt.hasNext() ? pathInfo.get(idx + 1) : null;
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
                throw new IllegalStateException("Unexpected token '" + token + "' with previousToken '" + previousToken + "' and nextToken '" + nextToken + "' in " + pathInfo.stream().collect(Collectors.joining("/")));
            }
            idx++;
        }
        // EXTRACT TRAILING PATH
        if (!pathInfoTokensIt.hasNext()) {
            return new ParsedJobUrl(jobName, null, jobUrlPattern);
        }
        Long runNumber;
        String token = pathInfoTokensIt.next();
        if ("lastBuild".equals(token)) {
            jobUrlPattern.add("lastBuild");
            if (pathInfoTokensIt.hasNext()) {
                String runSegment = pathInfoTokensIt.next();
                jobUrlPattern.add(runSegment);
                if ("artifact".equals(runSegment)) {
                    jobUrlPattern.add(":artifact");
                } else if ("descriptorByName".equals(runSegment)) {
                    jobUrlPattern.add(":descriptor");
                    if (pathInfoTokensIt.hasNext()) {
                        jobUrlPattern.add(":method");
                    }
                } else {
                    pathInfoTokensIt.forEachRemaining(jobUrlPattern::add);
                }
            }
            return new ParsedJobUrl(jobName, null, jobUrlPattern);
        } else if (StringUtils.isNumeric(token)) {
            runNumber = Long.parseLong(token);
            jobUrlPattern.add(":runNumber");
            if (pathInfoTokensIt.hasNext()) {
                String runSegment = pathInfoTokensIt.next();
                jobUrlPattern.add(runSegment);
                if ("artifact".equals(runSegment)) {
                    jobUrlPattern.add(":artifact");
                } else if ("descriptorByName".equals(runSegment)) {
                    jobUrlPattern.add(":descriptor");
                    if (pathInfoTokensIt.hasNext()) {
                        jobUrlPattern.add(":method");
                    }
                } else if ("execution".equals(runSegment)) {
                    jobUrlPattern.add(":execution");
                    jobUrlPattern.add("*");
                } else {
                    pathInfoTokensIt.forEachRemaining(jobUrlPattern::add);
                }
            }
            return new ParsedJobUrl(jobName, runNumber, jobUrlPattern);
        } else if ("descriptorByName".equals(token)) {
            jobUrlPattern.add("descriptorByName");
            jobUrlPattern.add(":descriptor");
            jobUrlPattern.add("*");
            return new ParsedJobUrl(jobName, null, jobUrlPattern);
        } else {
            jobUrlPattern.add(token);
            pathInfoTokensIt.forEachRemaining(jobUrlPattern::add);
            return new ParsedJobUrl(jobName, null, jobUrlPattern);
        }
    }

    public static class ParsedJobUrl {
        public ParsedJobUrl(List<String> jobName, @Nullable Long runNumber, List<String> urlPattern) {
            this(String.join("/", jobName),
                runNumber,
                "/" + String.join("/", urlPattern));
        }

        public ParsedJobUrl(@Nullable String jobName, @Nullable Long runNumber, String urlPattern) {
            this.jobName = jobName;
            this.urlPattern = urlPattern;
            this.runNumber = runNumber;
        }

        @Nullable
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

    public static class ParsedBlueOceanPipelineJobUrl extends ParsedJobUrl {
        // /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/19/log/
        final String flowNodeId;
        final Integer stepId;

        public ParsedBlueOceanPipelineJobUrl(List<String> jobName, @Nullable Long runNumber, List<String> urlPattern, @Nullable String flowNodeId, @Nullable Integer stepId) {
            super(jobName, runNumber, urlPattern);
            this.flowNodeId = flowNodeId;
            this.stepId = stepId;
        }

        public ParsedBlueOceanPipelineJobUrl(@Nullable String jobName, @Nullable Long runNumber, @Nullable String flowNodeId, @Nullable Integer stepId, @Nullable String urlPattern) {
            super(jobName, runNumber, urlPattern);
            this.flowNodeId = flowNodeId;
            this.stepId = stepId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ParsedBlueOceanPipelineJobUrl that = (ParsedBlueOceanPipelineJobUrl) o;
            return Objects.equals(flowNodeId, that.flowNodeId) && Objects.equals(stepId, that.stepId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), flowNodeId, stepId);
        }

        @Override
        public String toString() {
            return "ParsedBlueOceanPipelineJobUrl{" +
                "jobName='" + jobName + '\'' +
                ", runNumber=" + runNumber +
                ", flowNodeId='" + flowNodeId + '\'' +
                ", stepId=" + stepId +
                ", urlPattern='" + urlPattern + '\'' +
                '}';
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getClass());
    }

    @Inject
    public void setTracer(ReconfigurableOpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("io.jenkins.stapler");
        this.meter = openTelemetry.getMeter("io.jenkins.stapler");
    }
}
