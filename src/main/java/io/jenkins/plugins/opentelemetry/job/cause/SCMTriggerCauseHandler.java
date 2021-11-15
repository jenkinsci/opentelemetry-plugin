/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import hudson.Extension;
import hudson.model.Cause;
import hudson.triggers.SCMTrigger;
import jenkins.YesNoMaybe;

import javax.annotation.Nonnull;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class SCMTriggerCauseHandler implements CauseHandler {

    public SCMTriggerCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(SCMTrigger.SCMTriggerCause.class.getName());
    }

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return cause instanceof SCMTrigger.SCMTriggerCause;
    }

    /**
     * After {@link  GitHubPushCauseHandler}, {@link GitLabWebHookCauseHandler}, and {@link BitBucketPushCauseHandler}
     */
    @Override
    public int ordinal() {
        return 1000;
    }
}
