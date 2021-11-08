/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import com.cloudbees.jenkins.GitHubPushCause;
import hudson.Extension;
import hudson.model.Cause;
import jenkins.YesNoMaybe;

import javax.annotation.Nonnull;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class GitHubPushCauseHandler implements CauseHandler {

    public GitHubPushCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(GitHubPushCause.class.getName());
    }

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return cause instanceof GitHubPushCause;
    }

    @Override
    public String getStructuredDescription(@Nonnull Cause cause) {
        // https://github.com/jenkinsci/github-plugin/blob/master/src/main/java/com/cloudbees/jenkins/GitHubPushCause.java#L39
        String id = cause.getShortDescription().replaceAll(".* by ", "");
        return cause.getClass().getSimpleName() + ":" + id;
    }
}
