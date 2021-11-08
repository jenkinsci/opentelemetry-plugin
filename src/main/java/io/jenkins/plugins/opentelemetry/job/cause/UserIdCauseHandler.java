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
public class UserIdCauseHandler implements CauseHandler {

    public UserIdCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(Cause.UserIdCause.class.getName());
    }

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return cause instanceof Cause.UserIdCause;
    }

    @Nonnull
    @Override
    public String getStructuredDescription(@Nonnull Cause cause) {
        Cause.UserIdCause userIdCause = (Cause.UserIdCause) cause;
        String id = userIdCause.getUserId();
        if (id == null) {
            id = userIdCause.getUserName();
        }
        return cause.getClass().getSimpleName() + ":" + id;
    }
}
