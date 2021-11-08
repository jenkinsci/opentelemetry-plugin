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
public class BitBucketPushCauseHandler implements CauseHandler {

    @Override
    public boolean isSupported(@Nonnull Cause cause) {
        return isBitBucketPushCause(cause);
    }

    protected boolean isBitBucketPushCause(Cause cause) {
        return cause instanceof com.cloudbees.jenkins.plugins.BitBucketPushCause;
    }

    @Override
    public String getStructuredDescription(@Nonnull Cause cause)  {
        // https://github.com/jenkinsci/bitbucket-plugin/blob/master/src/main/java/com/cloudbees/jenkins/plugins/BitBucketPushCause.java#L33
        String id = cause.getShortDescription().replaceAll(".* by ", "");
        return cause.getClass().getSimpleName()  + ":" + id;
    }
}
