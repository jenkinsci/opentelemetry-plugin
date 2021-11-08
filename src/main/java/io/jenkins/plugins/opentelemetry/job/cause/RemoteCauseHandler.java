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
public class RemoteCauseHandler implements CauseHandler {

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return (cause instanceof Cause.RemoteCause);
    }

    @Override
    public String getStructuredDescription(@Nonnull Cause cause)  {
        Cause.RemoteCause remoteCause = (Cause.RemoteCause) cause;
        return cause.getClass().getSimpleName() + ":" + remoteCause.getAddr();
    }
}
