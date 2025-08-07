/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.scm.SCM;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.YesNoMaybe;
import jenkins.branch.Branch;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;

/**
 * Customization of the {@code checkout ...} step when configured to access a Git repository.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class GitCheckoutStepHandler extends AbstractGitStepHandler {
    private static final Logger LOGGER = Logger.getLogger(GitCheckoutStepHandler.class.getName());

    @Override
    public boolean canCreateSpanBuilder(@NonNull FlowNode flowNode, @NonNull WorkflowRun run) {
        if (!(flowNode instanceof StepAtomNode)) {
            return false;
        }
        StepAtomNode stepAtomNode = (StepAtomNode) flowNode;
        if (!(stepAtomNode.getDescriptor() instanceof GenericSCMStep.DescriptorImpl)) {
            return false;
        }

        WorkflowJob pipeline = run.getParent();
        BranchJobProperty branchJobProperty = pipeline.getProperty(BranchJobProperty.class);
        if (branchJobProperty == null) {
            // FIXME implement generic `checkout ...` step
            Map<String, Object> rootArguments = ArgumentsAction.getFilteredArguments(flowNode);
            Object scmAsObject = rootArguments.get("scm");
            if (scmAsObject == null) {
                return false;
            }
            if (!(scmAsObject instanceof Map)) {
                // `scm` is sometime a org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable .
                // See https://github.com/jenkinsci/opentelemetry-plugin/issues/467
                LOGGER.log(
                        Level.FINE,
                        () -> "Skip unexpected 'scm' type: "
                                + scmAsObject.getClass().getSimpleName() + ": " + scmAsObject);
                return false;
            }
            Map<String, ?> scm = (Map<String, ?>) scmAsObject;

            Object clazz = scm.get("$class");
            return Objects.equal(GitSCM.class.getSimpleName(), clazz);
        } else {
            // MultiBranch Pipeline using Git
            final SCM scm = branchJobProperty.getBranch().getScm();
            return scm instanceof GitSCM;
        }
    }

    @NonNull
    @Override
    public SpanBuilder createSpanBuilder(@NonNull FlowNode node, @NonNull WorkflowRun run, @NonNull Tracer tracer) {
        final Map<String, ?> rootArguments = ArgumentsAction.getFilteredArguments(node);
        final String stepFunctionName = node.getDisplayFunctionName();
        LOGGER.log(Level.FINE, () -> stepFunctionName + " - begin " + rootArguments);

        boolean shallow = false;
        int depth = 0;

        final BranchJobProperty branchJobProperty = run.getParent().getProperty(BranchJobProperty.class);

        // TODO better handling of cases where an expected property is not found
        if (branchJobProperty == null) {
            final Map<String, Object> scm = (Map<String, Object>) rootArguments.get("scm");
            if (scm == null) {
                return addCloneAttributes(tracer.spanBuilder(stepFunctionName), shallow, depth);
            }
            final Object clazz = scm.get("$class");
            if (!(Objects.equal(GitSCM.class.getSimpleName(), clazz))) {
                return addCloneAttributes(tracer.spanBuilder(stepFunctionName), shallow, depth);
            }
            List<Map<String, Object>> extensions = (List<Map<String, Object>>) scm.getOrDefault("extensions", List.of());
            final Map<String, ?> cloneOption = Iterables.getFirst(extensions, null);

            if (cloneOption != null) {
                shallow = cloneOption.containsKey("shallow") ? (Boolean) cloneOption.get("shallow") : shallow;
                depth = cloneOption.containsKey("depth") ? (Integer) cloneOption.get("depth") : depth;
            }

            List<Map<String, Object>> userRemoteConfigs = (List<Map<String, Object>>) scm.getOrDefault("userRemoteConfigs",List.of());
            final Map<String, ?> userRemoteConfig = Iterables.getFirst(userRemoteConfigs, null);
            if (userRemoteConfig == null) {
                return addCloneAttributes(tracer.spanBuilder(stepFunctionName), shallow, depth);
            }
            String gitUrl = (String) userRemoteConfig.get("url");
            String credentialsId = (String) userRemoteConfig.get("credentialsId");

            final List<Map<String, ?>> branches = (List<Map<String, ?>>) scm.get("branches");
            String gitBranch;
            if (branches == null) {
                gitBranch = null;
            } else {
                final Map<String, ?> branch = Iterables.getFirst(branches, null);
                gitBranch = (String) branch.get("name");
            }
            return addCloneAttributes(
                    super.createSpanBuilder(gitUrl, gitBranch, credentialsId, stepFunctionName, tracer, run),
                    shallow,
                    depth);
        } else {
            Branch branch = branchJobProperty.getBranch();
            String gitBranch = branch.getName();

            final SCM scm = branch.getScm();
            if (scm instanceof GitSCM gitScm) {
                CloneOption clone = gitScm.getExtensions().get(CloneOption.class);
                if (clone != null && clone.isShallow()) {
                    if (clone.getDepth() != null) {
                        depth = clone.getDepth();
                    }
                    shallow = clone.isShallow();
                }
                UserRemoteConfig userRemoteConfig = Iterables.getFirst(gitScm.getUserRemoteConfigs(), null);
                if (userRemoteConfig == null) {
                    return addCloneAttributes(tracer.spanBuilder(stepFunctionName), shallow, depth);
                }
                String gitUrl = userRemoteConfig.getUrl();
                String credentialsId = userRemoteConfig.getCredentialsId();

                if (Strings.isNullOrEmpty(gitUrl)) {
                    return addCloneAttributes(tracer.spanBuilder(stepFunctionName), shallow, depth);
                }

                return addCloneAttributes(
                        super.createSpanBuilder(gitUrl, gitBranch, credentialsId, stepFunctionName, tracer, run),
                        shallow,
                        depth);
            } else {
                return addCloneAttributes(tracer.spanBuilder(stepFunctionName), shallow, depth);
            }
        }
    }

    private SpanBuilder addCloneAttributes(@NonNull SpanBuilder spanBuilder, boolean shallow, int depth) {
        return spanBuilder
                .setAttribute(ExtendedJenkinsAttributes.GIT_CLONE_DEPTH, (long) depth)
                .setAttribute(ExtendedJenkinsAttributes.GIT_CLONE_SHALLOW, shallow);
    }
}
