/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.PeerIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.net.URISyntaxException;

public abstract class AbstractGitStepHandler implements StepHandler {

    public String searchGitUserName(@Nullable String credentialsId, @NonNull WorkflowRun run) {
        if (credentialsId == null) {
            return null;
        }

        String gitUserName = credentialsId;
        StandardUsernameCredentials credentials = CredentialsProvider.findCredentialById(credentialsId,
                StandardUsernameCredentials.class,
                run);
        if (credentials != null && credentials.getUsername() != null) {
            gitUserName = credentials.getUsername();
        }

        return gitUserName;
    }

    /**
     * See:
     * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/rpc.md
     * https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols
     */
    @VisibleForTesting
    @NonNull
    public SpanBuilder createSpanBuilder(@NonNull String gitUrl, @Nullable String gitBranch, @Nullable String credentialsId, @NonNull String stepFunctionName, @NonNull Tracer tracer, @NonNull WorkflowRun run) {
        String gitUserName = searchGitUserName(credentialsId, run);
        return createSpanBuilderFromGitDetails(gitUrl, gitBranch, gitUserName, stepFunctionName, tracer);
    }

    /**
     * Visible for testing. User {@link #createSpanBuilder(String, String, String, String, Tracer, WorkflowRun)} instead of this method
     */
    @VisibleForTesting
    protected SpanBuilder createSpanBuilderFromGitDetails(@Nullable String gitUrl, @Nullable String gitBranch, @Nullable String gitUserName, @NonNull String stepFunctionName, @NonNull Tracer tracer) {
        URIish gitUri;
        try {
            gitUri = new URIish(gitUrl);
        } catch (URISyntaxException e) {
            return tracer.spanBuilder(stepFunctionName);
        }
        String host = gitUri.getHost();
        String gitRepositoryPath = normalizeGitRepositoryPath(gitUri);

        final SpanBuilder spanBuilder;
        if ("https".equals(gitUri.getScheme())) {
            // HTTPS URL e.g. https://github.com/open-telemetry/opentelemetry-java

            String spanName = stepFunctionName + ": " + host + "/" + gitRepositoryPath;
            spanBuilder = tracer.spanBuilder(spanName);
            spanBuilder
                    .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, gitUri.getScheme())
                    .setAttribute(RpcIncubatingAttributes.RPC_SERVICE, "git")
                    .setAttribute(RpcIncubatingAttributes.RPC_METHOD, "checkout")
                    .setAttribute(ServerAttributes.SERVER_ADDRESS, host)
                    .setAttribute(PeerIncubatingAttributes.PEER_SERVICE, host)
                    .setAttribute(UrlAttributes.URL_FULL, sanitizeUrl(gitUri))
                    .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "POST")
                    .setSpanKind(SpanKind.CLIENT)
            ;

        } else if ("ssh".equals(gitUri.getScheme())
                || (gitUri.getScheme() == null && gitUri.getHost() != null)) {
            // SSH URL e.g. ssh://git@example.com/open-telemetry/opentelemetry-java.git
            // SCP URL e.g. git@github.com:open-telemetry/opentelemetry-java.git

            String spanName = stepFunctionName + ": " + host + "/" + gitRepositoryPath;
            spanBuilder = tracer.spanBuilder(spanName);
            spanBuilder
                    .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "ssh")
                    .setAttribute(RpcIncubatingAttributes.RPC_SERVICE, "git")
                    .setAttribute(RpcIncubatingAttributes.RPC_METHOD, "checkout")
                    .setAttribute(ServerAttributes.SERVER_ADDRESS, host)
                    .setAttribute(PeerIncubatingAttributes.PEER_SERVICE, host)
                    .setAttribute(NetworkAttributes.NETWORK_TRANSPORT, NetworkAttributes.NetworkTransportValues.TCP)
                    .setSpanKind(SpanKind.CLIENT)
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

        spanBuilder.setAttribute(ExtendedJenkinsAttributes.GIT_REPOSITORY, gitRepositoryPath);
        if (!Strings.isNullOrEmpty(gitBranch)) {
            spanBuilder.setAttribute(ExtendedJenkinsAttributes.GIT_BRANCH, gitBranch);
        }
        if (!Strings.isNullOrEmpty(gitUserName)) {
            spanBuilder.setAttribute(ExtendedJenkinsAttributes.GIT_USERNAME, gitUserName);
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
    @NonNull
    protected String sanitizeUrl(@NonNull URIish gitUri) {
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
