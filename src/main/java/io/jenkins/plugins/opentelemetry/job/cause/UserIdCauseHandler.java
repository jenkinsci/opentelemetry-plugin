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
public class UserIdCauseHandler extends AbstractCauseHandler {

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return (cause instanceof Cause.UserIdCause);
    }

    private Cause.UserIdCause getCause(@Nonnull Cause cause) {
        return ((Cause.UserIdCause) cause);
    }

    @Override
    public String getDetails(@Nonnull Cause cause)  {
        String id = getCause(cause).getUserId();
        if (id == null) {
            id = getCause(cause).getUserName();
        }
        return ":" + id;
    }
}
