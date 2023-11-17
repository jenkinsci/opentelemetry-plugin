/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.servlet;

import hudson.model.User;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.SemanticAttributes;
import org.apache.commons.lang.StringUtils;

import edu.umd.cs.findbugs.annotations.Nullable;
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
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

/**
 * Instrumentation of the Stapler MVC framework.
 *
 * TODO find a smarter way to instrument each HTTP request path. It should rely on instrumenting the Stapler framework
 * TODO adopt <a href="https://javadoc.jenkins.io/component/stapler/org/kohsuke/stapler/StaplerRequest.html#getAncestors()">StaplerRequest.html#getAncestors()</a>
 */
public class StaplerInstrumentationServletFilter implements Filter {
    private final static Logger logger = Logger.getLogger(StaplerInstrumentationServletFilter.class.getName());
    private final Tracer tracer;

    private static final Set<String> SKIP_PATHS = new HashSet<>(Arrays.asList("static", "adjuncts", "scripts", "plugin", "images", "sse-gateway"));

    public StaplerInstrumentationServletFilter(Tracer tracer) {
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
        // The matched route (path template).
        String httpRoute;
        if (SKIP_PATHS.contains(rootPath)) {
            // skip
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        if (rootPath.equals("$stapler")) {
            // TODO handle URL pattern /$stapler/bound/ec328aeb-26be-43da-94a3-59f2d683131c/news
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        SpanBuilder spanBuilder;
        try {
            if (rootPath.equals("job")) {
                // e.g /job/my-war/job/master/lastBuild/console
                // e.g /job/my-war/job/master/2/console
                ParsedJobUrl parsedJobUrl = parseJobUrl(pathInfoTokens);
                httpRoute = parsedJobUrl.urlPattern;
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + parsedJobUrl.urlPattern);
                if (parsedJobUrl.jobName != null) {
                    spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, parsedJobUrl.jobName);
                }
                if (parsedJobUrl.runNumber != null) {
                    spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, parsedJobUrl.runNumber);
                }
            } else if (rootPath.equals("blue")) {
                if (pathInfoTokens.size() == 1) {
                    httpRoute = "/blue/";
                    spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + httpRoute);
                } else if ("rest".equals(pathInfoTokens.get(1))) {
                    if (pathInfoTokens.size() == 2) {
                        httpRoute = "/blue/rest/";
                        spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + httpRoute);
                    } else if ("organizations".equals(pathInfoTokens.get(2)) && pathInfoTokens.size() > 7) {
                        // eg /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/blueTestSummary/

                        ParsedJobUrl parsedBlueOceanPipelineUrl = parseBlueOceanRestPipelineUrl(pathInfoTokens);
                        httpRoute = parsedBlueOceanPipelineUrl.urlPattern;
                        spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + parsedBlueOceanPipelineUrl.urlPattern);
                        if (parsedBlueOceanPipelineUrl.jobName != null) {
                            spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, parsedBlueOceanPipelineUrl.jobName);
                        }
                        if (parsedBlueOceanPipelineUrl.runNumber != null) {
                            spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, parsedBlueOceanPipelineUrl.runNumber);
                        }
                    } else if ("classes".equals(pathInfoTokens.get(2)) && pathInfoTokens.size() > 3) {
                        // eg /blue/rest/classes/io.jenkins.blueocean.rest.impl.pipeline.PipelineRunImpl/
                        String blueOceanClass = pathInfoTokens.get(3);
                        httpRoute = "/blue/rest/classes/:blueOceanClass";
                        spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + httpRoute)
                            .setAttribute("blueOceanClass", blueOceanClass);
                    } else {
                        // eg /blue/rest/i18n/blueocean-personalization/1.25.2/jenkins.plugins.blueocean.personalization.Messages/en-US
                        httpRoute = "/blue/rest/" + pathInfoTokens.get(2) + "/*";
                        spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + httpRoute);
                    }
                } else {
                    httpRoute = "/blue/" + pathInfoTokens.get(1) + "/*";
                    spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + httpRoute);
                }

            } else if (rootPath.equals("administrativeMonitor")) {
                // eg GET /administrativeMonitor/hudson.diagnosis.ReverseProxySetupMonitor/testForReverseProxySetup/http://localhost:8080/jenkins/manage/
                httpRoute = "/administrativeMonitor/:administrativeMonitor/*";
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/administrativeMonitor/");
                if (pathInfoTokens.size() > 1) {
                    spanBuilder.setAttribute("administrativeMonitor", pathInfoTokens.get(1));
                }
            } else if (rootPath.equals("asynchPeople")) {
                httpRoute = "/asynchPeople";
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/asynchPeople");
            } else if (rootPath.equals("computer")) {
                // /computer/(master)/
                httpRoute = "/computer/:computer/*";
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/computer/*");
                // TODO more details
            } else if (rootPath.equals("credentials")) {
                // eg /credentials/store/system/domain/_/
                httpRoute = "/credentials/store/:store/domain/:domain/*";
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/credentials/*");
                // TODO more details
            } else if (rootPath.equals("descriptorByName")) {
                httpRoute = "/descriptorByName/:descriptor/*";
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/descriptorByName/");
                if (pathInfoTokens.size() > 1) {
                    spanBuilder.setAttribute("descriptor", pathInfoTokens.get(1));
                }
            } else if (rootPath.equals("extensionList")) {
                // eg /extensionList/hudson.diagnosis.MemoryUsageMonitor/0/heap/graph
                httpRoute = "/extensionList/:extension/*";
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/extensionList/");
                if (pathInfoTokens.size() > 1) {
                    spanBuilder.setAttribute("extension", pathInfoTokens.get(1));
                }
            } else if (rootPath.equals("fingerprint")) {
                httpRoute = "/fingerprint/:fingerprint";
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/fingerprint/");
                spanBuilder.setAttribute("fingerprint", pathInfo.substring("/fingerprint/".length()));
                if (pathInfoTokens.size() > 1) {
                    spanBuilder.setAttribute("fingerprint", pathInfoTokens.get(1));
                }
            } else if (rootPath.equals("user")) {
                //eg /user/cyrille.leclerc/ /user/cyrille.leclerc/configure /user/cyrille.leclerc/my-views/view/all/
                httpRoute = "/user/:user/*";
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + "/user/*");
                if (pathInfoTokens.size() > 1) {
                    spanBuilder.setAttribute("user", pathInfoTokens.get(1));
                }
            } else {
                httpRoute = "/" + rootPath + "/*";
                spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + pathInfo);
            }
        } catch (RuntimeException e) {
            httpRoute = "/" + rootPath + "/*";
            spanBuilder = tracer.spanBuilder(servletRequest.getMethod() + " " + pathInfo);
            logger.log(Level.INFO, () -> "Exception processing URL " + pathInfo + ", default to httpRoute: '/" + rootPath + "/*': " + e);
        }


        String httpTarget = servletRequest.getRequestURI();
        String queryString = servletRequest.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            httpTarget += "?" + queryString;
        }

        Thread currentThread = Thread.currentThread();
        spanBuilder
            .setAttribute(SemanticAttributes.HTTP_CLIENT_IP, servletRequest.getRemoteAddr())
            .setAttribute(SemanticAttributes.HTTP_SCHEME, servletRequest.getScheme())
            .setAttribute(SemanticAttributes.NET_HOST_NAME, servletRequest.getServerName())
            .setAttribute(SemanticAttributes.NET_HOST_NAME, servletRequest.getServerName() + ":" + servletRequest.getServerPort())
            .setAttribute(SemanticAttributes.HTTP_METHOD, servletRequest.getMethod())
            .setAttribute(SemanticAttributes.HTTP_TARGET, httpTarget)
            .setAttribute(SemanticAttributes.HTTP_ROUTE, httpRoute)
            .setAttribute(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP)
            .setAttribute(SemanticAttributes.NET_SOCK_PEER_ADDR, servletRequest.getRemoteAddr())
            .setAttribute(SemanticAttributes.NET_SOCK_PEER_PORT, (long) servletRequest.getRemotePort())
            .setAttribute(SemanticAttributes.THREAD_NAME, currentThread.getName())
            .setAttribute(SemanticAttributes.THREAD_ID, currentThread.getId())
            .setSpanKind(SpanKind.SERVER);

        User user = User.current();
        if (user != null) {
            spanBuilder.setAttribute(SemanticAttributes.ENDUSER_ID, user.getId());
        }

        Span span = spanBuilder.startSpan();
        try (Scope scope = span.makeCurrent()) {
            filterChain.doFilter(servletRequest, servletResponse);
            span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, servletResponse.getStatus());
        } finally {
            span.end();
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
            throw new IllegalArgumentException("Job URL.pathInfo doesn't start with '/job': " + pathInfo.stream().collect(Collectors.joining("/")));
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
            this(jobName.stream().collect(Collectors.joining("/")),
                runNumber,
                "/" + urlPattern.stream().collect(Collectors.joining("/")));
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
    public void destroy() {

    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return StaplerInstrumentationServletFilter.class.hashCode();
    }
}
