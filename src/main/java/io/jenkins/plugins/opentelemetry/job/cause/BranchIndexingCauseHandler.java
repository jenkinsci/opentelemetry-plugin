/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import hudson.Extension;
import hudson.model.Cause;
import jenkins.YesNoMaybe;
import jenkins.branch.BranchIndexingCause;

import javax.annotation.Nonnull;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class BranchIndexingCauseHandler implements CauseHandler {

    public BranchIndexingCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(BranchIndexingCause.class.getName());
    }

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return cause instanceof jenkins.branch.BranchIndexingCause;
    }
}
