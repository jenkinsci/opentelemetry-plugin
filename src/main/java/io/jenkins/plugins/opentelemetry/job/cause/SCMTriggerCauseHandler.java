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
public class SCMTriggerCauseHandler extends AbstractCauseHandler {

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return (cause instanceof SCMTrigger.SCMTriggerCause && !isGitHubPushCause(cause) && !isGitLabWebHookCause(cause) && !isBitBucketPushCause(cause));
    }
}
