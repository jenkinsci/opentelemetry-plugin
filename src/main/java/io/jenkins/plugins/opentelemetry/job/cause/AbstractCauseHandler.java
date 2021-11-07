/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import hudson.Extension;
import hudson.model.Cause;
import jenkins.YesNoMaybe;

import javax.annotation.Nonnull;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class AbstractCauseHandler implements CauseHandler {

    private String structuredDescription;

    @Override
    public boolean canAddAttributes(@Nonnull Cause cause) {
        if (isSupported(cause)) {
            setStructuredDescription(cause.getClass().getSimpleName() + getDetails(cause));
            return true;
        }
        return false;
    }

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return false;
    }

    @Nonnull
    @Override
    public String getStructuredDescription() {
        return this.structuredDescription;
    }

    /**
     * @return Empty or prefixed by ":"
     */
    @Nonnull
    public String getDetails(@Nonnull Cause cause) {
        return "";
    }

    protected void setStructuredDescription(String xxx) {
        this.structuredDescription = xxx;
    }

    protected boolean isGitHubPushCause(Cause cause) {
        return cause.getClass().getName().equals("com.cloudbees.jenkins.GitHubPushCause");
    }

    protected boolean isGitLabWebHookCause(Cause cause) {
        return cause.getClass().getName().equals("com.dabsquared.gitlabjenkins.cause.GitLabWebHookCause");
    }

    protected boolean isBitBucketPushCause(Cause cause) {
        return cause.getClass().getName().equals("com.cloudbees.jenkins.plugins.BitBucketPushCause");
    }

    protected boolean isBranchIndexingCause(Cause cause) {
        return cause.getClass().getName().equals("jenkins.branch.BranchIndexingCause");
    }
}
