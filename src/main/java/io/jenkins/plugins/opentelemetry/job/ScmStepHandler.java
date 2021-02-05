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
    public void handle(@Nonnull FlowNode node, @Nonnull SpanBuilder spanBuilder)  throws Exception {

        handle(ArgumentsAction.getFilteredArguments(node), spanBuilder);
    }

    protected void handle(@Nonnull Map<String, Object> arguments, @Nonnull SpanBuilder spanBuilder) throws Exception {
        // see https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/rpc.md#attributes

        String gitUrlAsString = checkNotNull(arguments.get("url")).toString();
        if (gitUrlAsString.startsWith("http")) {
            URL gitUrl = new URL(gitUrlAsString);
            String githubOrgAndRepository = gitUrl.getPath().substring(1); //remove beginning '/'
            if (githubOrgAndRepository.endsWith(".git")){
                githubOrgAndRepository = githubOrgAndRepository.substring(0, githubOrgAndRepository.length() - ".git".length());
            }

            spanBuilder
                    .setAttribute(SemanticAttributes.RPC_SYSTEM, gitUrl.getProtocol())
                    .setAttribute(SemanticAttributes.RPC_SERVICE, "git")
                    .setAttribute(SemanticAttributes.RPC_METHOD, "checkout")
                    .setAttribute(SemanticAttributes.NET_PEER_NAME, gitUrl.getHost())
                    .setAttribute(SemanticAttributes.PEER_SERVICE, "github")
                    .setAttribute(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP.getValue())
                    .setAttribute(SemanticAttributes.HTTP_URL, gitUrl.toString())
                    .setAttribute(SemanticAttributes.HTTP_METHOD, "POST") // TODO verify value, this attribute is required to trace HTTP calls
                    .setAttribute(JenkinsOtelSemanticAttributes.GIT_REPOSITORY, githubOrgAndRepository)
            ;
            if (gitUrl.getPort() == -1) {

            } else {
                spanBuilder = spanBuilder.setAttribute(SemanticAttributes.NET_PEER_PORT, Long.valueOf(gitUrl.getPort()));
            }
        } else {
            // TODO handle SSH
        }
    }
}
