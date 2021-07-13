/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.slaves.NodeProvisioner;
import jenkins.YesNoMaybe;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * Use same root span name for all pull cloud labels
 */
@Extension(dynamicLoadable = YesNoMaybe.YES)
@Symbol("cloudSpanNamingStrategy")
public class CloudSpanNamingStrategy {

    @DataBoundConstructor
    public CloudSpanNamingStrategy() {
    }

    @Nonnull
    public String getRootSpanName(@Nonnull NodeProvisioner.PlannedNode plannedNode) {
        return getNodeRootSpanName(plannedNode.displayName);
    }

    @VisibleForTesting
    @Nonnull
    protected String getNodeRootSpanName(@Nonnull String displayName) {
        // format: <namePrefix>-<id>
        // e.g. "obs11-ubuntu-18-linux-beyyg2"
        // remove last -<.*>
        if (displayName.contains("-")) {
            return displayName.substring(0, displayName.lastIndexOf("-")) + "-{id}";
        }
        return displayName;
    }
}
