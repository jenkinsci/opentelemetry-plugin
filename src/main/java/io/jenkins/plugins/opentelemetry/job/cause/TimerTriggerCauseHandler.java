/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import hudson.Extension;
import hudson.model.Cause;
import hudson.triggers.TimerTrigger;
import jenkins.YesNoMaybe;

import javax.annotation.Nonnull;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class TimerTriggerCauseHandler extends AbstractCauseHandler {

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return (cause instanceof TimerTrigger.TimerTriggerCause);
    }
}