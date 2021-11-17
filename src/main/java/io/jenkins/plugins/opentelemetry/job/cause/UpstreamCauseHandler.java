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
public class UpstreamCauseHandler implements CauseHandler {

    public UpstreamCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(Cause.UpstreamCause.class.getName());
    }

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return (cause instanceof Cause.UpstreamCause);
    }

    @Override
    public String getStructuredDescription(@Nonnull Cause cause) {
        Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;
        return upstreamCause.getClass().getSimpleName() + ":" + upstreamCause.getUpstreamProject() + "#" + upstreamCause.getUpstreamBuild();
    }
}
