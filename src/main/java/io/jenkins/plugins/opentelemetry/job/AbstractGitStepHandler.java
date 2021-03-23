/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractGitStepHandler implements StepHandler {
    private final static Logger LOGGER = Logger.getLogger(AbstractGitStepHandler.class.getName());

    /**
     * See:
     * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/rpc.md
     * https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols
     */
    @VisibleForTesting
    @Nonnull
    public SpanBuilder createSpanBuilder(@Nonnull String gitUrl, @Nullable String gitBranch, @Nullable String credentialsId, @Nonnull String stepFunctionName, @Nonnull Tracer tracer, @Nonnull WorkflowRun run) throws URISyntaxException {
        // FIXME retrieve the git username from the credentialsId, we need to lookup in the context of the run
        String gitUserName = credentialsId;

        return createSpanBuilderFromGitDetails(gitUrl, gitBranch, gitUserName, stepFunctionName, tracer);
    }

    /**
     * Visible for testing. User {@link #createSpanBuilder(String, String, String, String, Tracer, WorkflowRun)} instead of this method
     */
    @VisibleForTesting
    protected SpanBuilder createSpanBuilderFromGitDetails(@Nullable String gitUrl, @Nullable String gitBranch, @Nullable String gitUserName, @Nonnull String stepFunctionName, @Nonnull Tracer tracer) throws URISyntaxException {
        URIish gitUri = new URIish(gitUrl);
        String host = gitUri.getHost();
        String gitRepositoryPath = normalizeGitRepositoryPath(gitUri);

        final SpanBuilder spanBuilder;
        if ("https".equals(gitUri.getScheme())) {
            // HTTPS URL e.g. https://github.com/open-telemetry/opentelemetry-java

            String spanName = stepFunctionName + ": " + host + "/" + gitRepositoryPath;
            spanBuilder = tracer.spanBuilder(spanName);
            spanBuilder
                    .setAttribute(SemanticAttributes.RPC_SYSTEM, gitUri.getScheme())
                    .setAttribute(SemanticAttributes.RPC_SERVICE, "git")
                    .setAttribute(SemanticAttributes.RPC_METHOD, "checkout")
                    .setAttribute(SemanticAttributes.NET_PEER_NAME, host)
                    .setAttribute(SemanticAttributes.PEER_SERVICE, "git")
                    .setAttribute(SemanticAttributes.HTTP_URL, sanitizeUrl(gitUri))
                    .setAttribute(SemanticAttributes.HTTP_METHOD, "POST")
            ;

        } else if ("ssh".equals(gitUri.getScheme())
                || (gitUri.getScheme() == null && gitUri.getHost() != null)) {
            // SSH URL e.g. ssh://git@example.com/open-telemetry/opentelemetry-java.git
            // SCP URL e.g. git@github.com:open-telemetry/opentelemetry-java.git

            String spanName = stepFunctionName + ": " + host + "/" + gitRepositoryPath;
            spanBuilder = tracer.spanBuilder(spanName);
            spanBuilder
                    .setAttribute(SemanticAttributes.RPC_SYSTEM, "ssh")
                    .setAttribute(SemanticAttributes.RPC_SERVICE, "git")
                    .setAttribute(SemanticAttributes.RPC_METHOD, "checkout")
                    .setAttribute(SemanticAttributes.NET_PEER_NAME, host)
                    .setAttribute(SemanticAttributes.PEER_SERVICE, "git")
                    .setAttribute(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP.getValue())
            ;
        } else if (("file".equals(gitUri.getScheme())
                || (gitUri.getScheme() == null && gitUri.getHost() == null))) {
            // FILE URL e.g. file:///srv/git/project.git
            // IMPLICIT FILE URL e.g. /srv/git/project.git

            String spanName = stepFunctionName + ": " +  gitRepositoryPath;
            spanBuilder = tracer.spanBuilder(spanName);
        } else {
            // TODO document which case it is?
            String spanName = stepFunctionName + ": " + host + "/" + gitRepositoryPath;
            spanBuilder = tracer.spanBuilder(spanName);
        }

        spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.GIT_REPOSITORY, gitRepositoryPath);
        if (!Strings.isNullOrEmpty(gitBranch)) {
            spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.GIT_BRANCH, gitBranch);
        }
        if (!Strings.isNullOrEmpty(gitUserName)) {
            spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.GIT_USERNAME, gitUserName);
        }

        return spanBuilder;
    }

    private String normalizeGitRepositoryPath(URIish gitUri) {
        String githubOrgAndRepository = gitUri.getPath();
        if (githubOrgAndRepository.startsWith("/")) {
            githubOrgAndRepository = githubOrgAndRepository.substring("/".length());
        }
        if (githubOrgAndRepository.endsWith(".git")) {
            githubOrgAndRepository = githubOrgAndRepository.substring(0, githubOrgAndRepository.length() - ".git".length());
        }
        return githubOrgAndRepository;
    }

    /**
     * Remove the `username` and the `password` params of the URL.
     * <p>
     * Example: "https://my_username:my_password@github.com/open-telemetry/opentelemetry-java.git" is sanitized as
     * "https://github.com/open-telemetry/opentelemetry-java.git"
     *
     * @param gitUri to sanitize
     * @return sanitized url
     */
    @Nonnull
    protected String sanitizeUrl(@Nonnull URIish gitUri) {
        String normalizedUrl = gitUri.getScheme() + "://";
        if (gitUri.getHost() != null) {
            normalizedUrl += gitUri.getHost();
        }
        if (gitUri.getPort() != -1) {
            normalizedUrl += ":" + gitUri.getPort();
        }
        normalizedUrl += gitUri.getPath();
        return normalizedUrl;
    }
}
