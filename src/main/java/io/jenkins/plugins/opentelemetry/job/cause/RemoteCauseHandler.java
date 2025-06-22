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
public class RemoteCauseHandler implements CauseHandler {

    public RemoteCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(Cause.RemoteCause.class.getName());
    }

    @Override
    public boolean isSupported(@NonNull Cause cause) {
        return (cause instanceof Cause.RemoteCause);
    }

    @Override
    public String getStructuredDescription(@NonNull Cause cause) {
        Cause.RemoteCause remoteCause = (Cause.RemoteCause) cause;
        return cause.getClass().getSimpleName() + ":" + remoteCause.getAddr();
    }
}
