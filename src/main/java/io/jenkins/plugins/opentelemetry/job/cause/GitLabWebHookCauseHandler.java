/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import com.dabsquared.gitlabjenkins.cause.GitLabWebHookCause;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Cause;
import jenkins.YesNoMaybe;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class GitLabWebHookCauseHandler implements CauseHandler {

    public GitLabWebHookCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(GitLabWebHookCause.class.getName());
    }

    @Override
    public boolean isSupported(@NonNull Cause cause) {
        return cause instanceof GitLabWebHookCause;
    }

    @Override
    public String getStructuredDescription(@NonNull Cause cause) {
        // https://github.com/jenkinsci/gitlab-plugin/blob/master/src/main/resources/com/dabsquared/gitlabjenkins/cause/Messages.properties#L2
        String id = cause.getShortDescription().replaceAll(".* by ", "");
        return cause.getClass().getSimpleName() + ":" + id;
    }
}
