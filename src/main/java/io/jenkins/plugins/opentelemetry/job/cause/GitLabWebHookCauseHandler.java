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
public class GitLabWebHookCauseHandler implements CauseHandler  {

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return cause instanceof com.dabsquared.gitlabjenkins.cause.GitLabWebHookCause;
    }

    @Override
    public String getStructuredDescription(@Nonnull Cause cause)  {
        // https://github.com/jenkinsci/gitlab-plugin/blob/master/src/main/resources/com/dabsquared/gitlabjenkins/cause/Messages.properties#L2
        String id = cause.getShortDescription().replaceAll(".* by ", "");
        return cause.getClass().getSimpleName()  + ":" + id;
    }
}
