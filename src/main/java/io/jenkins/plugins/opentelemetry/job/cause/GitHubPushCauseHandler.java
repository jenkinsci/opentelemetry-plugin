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
public class GitHubPushCauseHandler extends AbstractCauseHandler {

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return isGitHubPushCause(cause);
    }

    @Override
    public String getDetails(@Nonnull Cause cause)  {
        // https://github.com/jenkinsci/github-plugin/blob/master/src/main/java/com/cloudbees/jenkins/GitHubPushCause.java#L39
        String id = cause.getShortDescription().replaceAll(".* by ", "");
        return ":" + id;
    }
}
