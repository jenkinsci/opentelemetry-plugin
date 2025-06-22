/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Cause;
import jenkins.YesNoMaybe;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class UserIdCauseHandler implements CauseHandler {

    public UserIdCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(Cause.UserIdCause.class.getName());
    }

    @Override
    public boolean isSupported(@NonNull Cause cause) {
        return cause instanceof Cause.UserIdCause;
    }

    @NonNull
    @Override
    public String getStructuredDescription(@NonNull Cause cause) {
        Cause.UserIdCause userIdCause = (Cause.UserIdCause) cause;
        String id = userIdCause.getUserId();
        if (id == null) {
            id = userIdCause.getUserName();
        }
        return cause.getClass().getSimpleName() + ":" + id;
    }
}
