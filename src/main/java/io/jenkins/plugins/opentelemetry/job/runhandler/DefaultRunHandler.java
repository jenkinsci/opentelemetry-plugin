/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.runhandler;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;

/**
 * Use same short pipeline name for all change request pipelines (pull request, merge request)
 * Use different short pipeline name for different branches.
 */
@Extension
public class DefaultRunHandler implements RunHandler {

    private static final List<String> CHANGE_REQUEST_JOB_NAME_SUFFIXES = Collections.unmodifiableList(Arrays.asList(
            "-" + ChangeRequestCheckoutStrategy.HEAD.name().toLowerCase(Locale.ENGLISH),
            "-" + ChangeRequestCheckoutStrategy.MERGE.name().toLowerCase(Locale.ENGLISH)));

    @Override
    public boolean matches(@NonNull Run<?, ?> run) {
        return true;
    }

    @NonNull
    @Override
    public String getPipelineShortName(@NonNull Run<?, ?> run) {
        SCMHead head = SCMHead.HeadByItem.findHead(run.getParent());
        String pipelineShortName;
        if (head instanceof ChangeRequestSCMHead) {
            pipelineShortName = getChangeRequestRootSpanName(run.getParent().getFullName());
        } else {
            pipelineShortName = run.getParent().getFullName();
        }
        return pipelineShortName;
    }

    @VisibleForTesting
    @NonNull
    protected String getChangeRequestRootSpanName(@NonNull String jobFullName) {
        // org.jenkinsci.plugins.github_branch_source.PullRequestGHEventSubscriber
        // com.cloudbees.jenkins.plugins.bitbucket.BitbucketSCMSource
        // jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy
        // e.g. "my-war/PR-2", "my-war/PR-2-merge", "my-war/PR-2-head"
        // io.jenkins.plugins.gitlabbranchsource.GitLabSCMSource
        // e.g. "my-war/MR-2", "my-war/MR-2-merge", "my-war/MR-2-head"
        String rootSpanName = jobFullName;
        for (String changeRequestJobNameSuffix : CHANGE_REQUEST_JOB_NAME_SUFFIXES) {
            if (rootSpanName.endsWith(changeRequestJobNameSuffix)) {
                rootSpanName = rootSpanName.substring(0, rootSpanName.length() - changeRequestJobNameSuffix.length());
                break;
            }
        }

        // remove digits
        while (Character.isDigit(rootSpanName.charAt(rootSpanName.length() - 1))) {
            rootSpanName = rootSpanName.substring(0, rootSpanName.length() - 1);
        }
        if ('-' == rootSpanName.charAt(rootSpanName.length() - 1)) {
            return rootSpanName + "{number}";
        }
        return jobFullName;
    }

    @Override
    public int ordinal() {
        return Integer.MAX_VALUE;
    }
}
