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
public class DefaultCauseHandler implements CauseHandler {

    @Override
    public boolean isSupported(@NonNull Cause cause) {
        return true;
    }

    @Override
    public int ordinal() {
        return Integer.MAX_VALUE;
    }
}
