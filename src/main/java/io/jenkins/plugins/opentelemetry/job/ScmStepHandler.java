/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.Nonnull;
import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;


@Extension
public class ScmStepHandler implements StepHandler {

    private final static Logger LOGGER = Logger.getLogger(ScmStepHandler.class.getName());

    @Override
    public boolean canHandle(@Nonnull FlowNode flowNode) {
        return flowNode instanceof StepAtomNode && ((StepAtomNode) flowNode).getDescriptor() instanceof GitStep.DescriptorImpl;
    }

    @Override
    public void handle(@Nonnull FlowNode node, @Nonnull SpanBuilder spanBuilder) throws Exception {

        handle(ArgumentsAction.getFilteredArguments(node), spanBuilder);
    }

    protected void handle(@Nonnull Map<String, Object> arguments, @Nonnull SpanBuilder spanBuilder) throws Exception {

        String gitUrlAsString = checkNotNull(arguments.get("url")).toString();
        if (gitUrlAsString.startsWith("http://") || gitUrlAsString.startsWith("https://")) {
            URL gitUrl = new URL(gitUrlAsString);
            String gitRepositoryPath = normalizeGitRepositoryPath(gitUrl.getPath());
            spanBuilder
                    .setAttribute(SemanticAttributes.RPC_SYSTEM, gitUrl.getProtocol())
                    .setAttribute(SemanticAttributes.RPC_SERVICE, "git")
                    .setAttribute(SemanticAttributes.RPC_METHOD, "checkout")
                    .setAttribute(SemanticAttributes.NET_PEER_NAME, gitUrl.getHost())
                    .setAttribute(SemanticAttributes.PEER_SERVICE, "git")
                    .setAttribute(SemanticAttributes.HTTP_URL, sanitizeUrl(gitUrl))
                    .setAttribute(SemanticAttributes.HTTP_METHOD, "POST")
                    .setAttribute(JenkinsOtelSemanticAttributes.GIT_REPOSITORY, gitRepositoryPath)
            ;
        } else if (gitUrlAsString.startsWith("file://")) { // e.g. file:///srv/git/project.git
            // TODO
        } else if (gitUrlAsString.startsWith("ssh://")) { // e.g. ssh://git@example.com/open-telemetry/opentelemetry-java.git
            // see https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/rpc.md#attributes

            String usernameAtHost = gitUrlAsString.substring("ssh://".length(), gitUrlAsString.indexOf('/', "ssh://".length()));
            String host = usernameAtHost.contains("@") ? usernameAtHost.substring(usernameAtHost.indexOf('@') + 1) : usernameAtHost;
            if (host.contains(":")) {
                host = host.substring(0, host.indexOf(':'));
            }
            String gitRepositoryPath = normalizeGitRepositoryPath(gitUrlAsString.substring(gitUrlAsString.indexOf('/', ("ssh://".length() + usernameAtHost.length())) + 1));

            spanBuilder
                    .setAttribute(SemanticAttributes.RPC_SYSTEM, "ssh")
                    .setAttribute(SemanticAttributes.RPC_SERVICE, "git")
                    .setAttribute(SemanticAttributes.RPC_METHOD, "checkout")
                    .setAttribute(SemanticAttributes.NET_PEER_NAME, host)
                    .setAttribute(SemanticAttributes.PEER_SERVICE, "git")
                    .setAttribute(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP.getValue())
                    .setAttribute(JenkinsOtelSemanticAttributes.GIT_REPOSITORY, gitRepositoryPath)
            ;
        } else if (gitUrlAsString.contains(":")) { // e.g. git@github.com:open-telemetry/opentelemetry-java.git
            // see https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/rpc.md#attributes

            String usernameAtHost = gitUrlAsString.substring(0, gitUrlAsString.indexOf(':'));
            String host = usernameAtHost.contains("@") ? usernameAtHost.substring(usernameAtHost.indexOf('@') + 1) : usernameAtHost;
            String gitRepositoryPath = normalizeGitRepositoryPath(gitUrlAsString.substring(gitUrlAsString.indexOf(':') + 1));

            spanBuilder
                    .setAttribute(SemanticAttributes.RPC_SYSTEM, "ssh")
                    .setAttribute(SemanticAttributes.RPC_SERVICE, "git")
                    .setAttribute(SemanticAttributes.RPC_METHOD, "checkout")
                    .setAttribute(SemanticAttributes.NET_PEER_NAME, host)
                    .setAttribute(SemanticAttributes.PEER_SERVICE, "git")
                    .setAttribute(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP.getValue())
                    .setAttribute(JenkinsOtelSemanticAttributes.GIT_REPOSITORY, gitRepositoryPath)
                    ;
        }
    }

    private String normalizeGitRepositoryPath(String gitRepositoryPath) {
        String githubOrgAndRepository = gitRepositoryPath.startsWith("/") ? gitRepositoryPath.substring(1) : gitRepositoryPath; //remove beginning '/'
        if (githubOrgAndRepository.endsWith(".git")) {
            githubOrgAndRepository = githubOrgAndRepository.substring(0, githubOrgAndRepository.length() - ".git".length());
        }
        return githubOrgAndRepository;
    }

    /**
     * Remove the `username` and the `password` params of the URL.
     * <p/>
     * Example: "https://my_username:my_password@github.com/open-telemetry/opentelemetry-java.git" is sanitized as
     * "https://github.com/open-telemetry/opentelemetry-java.git"
     *
     * @param url to sanitize
     * @return sanitized url
     */
    @Nonnull
    protected String sanitizeUrl(@Nonnull URL url) {
        String normalizedUrl = url.getProtocol() + "://" + url.getHost();
        if (url.getPort() != -1) {
            normalizedUrl += ":" + url.getPort();
        }
        normalizedUrl += url.getPath();
        return normalizedUrl;
    }
}
