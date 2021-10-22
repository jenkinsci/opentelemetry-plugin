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
public class UpstreamCauseHandler extends AbstractCauseHandler {

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return (cause instanceof Cause.UpstreamCause);
    }

    private Cause.UpstreamCause getCause(@Nonnull Object cause) {
        return ((Cause.UpstreamCause) cause);
    }

    @Override
    public String getDetails(@Nonnull Cause cause)  {
        return ":" + getCause(cause).getUpstreamProject() + "#" + getCause(cause).getUpstreamBuild();
    }
}
