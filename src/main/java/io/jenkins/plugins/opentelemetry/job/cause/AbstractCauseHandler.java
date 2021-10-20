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

    private String description;
    private String xxx;

    @Override
    public boolean canAddAttributes(@Nonnull Cause cause) {
        if (isSupported(cause)) {
            setDescription(cause.getShortDescription());
            setXxx(cause.getClass().getSimpleName() + getDetails(cause));
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
    public String getDescription() {
        return this.description;
    }

    @Nonnull
    @Override
    public String getXxx() {
        return this.xxx;
    }

    public String getDetails(@Nonnull Cause cause) {
        return "";
    }

    protected void setDescription(String description) {
        this.description = description;
    }

    protected void setXxx(String xxx) {
        this.xxx = xxx;
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
