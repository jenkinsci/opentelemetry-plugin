/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Cause;
import hudson.triggers.TimerTrigger;
import jenkins.YesNoMaybe;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class TimerTriggerCauseHandler implements CauseHandler {

    public TimerTriggerCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(TimerTrigger.TimerTriggerCause.class.getName());
    }

    @Override
    public boolean isSupported(@NonNull Cause cause) {
        return (cause instanceof TimerTrigger.TimerTriggerCause);
    }
}
