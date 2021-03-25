/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Run;
import jenkins.YesNoMaybe;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Use same root span name for all pull change request pipelines (pull request, merge request)
 * Use different span names for different branches.
 */
@Extension(dynamicLoadable = YesNoMaybe.YES)
@Symbol("basicSpanNamingStrategy")
public class SpanNamingStrategy {

    private final static List<String> CHANGE_REQUEST_JOB_NAME_SUFFIXES = Collections.unmodifiableList(Arrays.asList(
            "-" + ChangeRequestCheckoutStrategy.HEAD.name().toLowerCase(Locale.ENGLISH),
            "-" + ChangeRequestCheckoutStrategy.MERGE.name().toLowerCase(Locale.ENGLISH)));

    @DataBoundConstructor
    public SpanNamingStrategy() {

    }

    @Nonnull
    public String getRootSpanName(@Nonnull Run run) {
        final SCMHead head = SCMHead.HeadByItem.findHead(run.getParent());
        final String fullName = run.getParent().getFullName();
        if (head instanceof ChangeRequestSCMHead) {
            return getChangeRequestRootSpanName(fullName);
        } else {
            return fullName;
        }
    }

    @VisibleForTesting
    @Nonnull
    protected String getChangeRequestRootSpanName(@Nonnull String jobFullName) {
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
}
