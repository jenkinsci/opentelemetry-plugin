/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.cause;

import com.cloudbees.jenkins.plugins.BitBucketPushCause;
import hudson.Extension;
import hudson.model.Cause;
import jenkins.YesNoMaybe;

import edu.umd.cs.findbugs.annotations.NonNull;

@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class BitBucketPushCauseHandler implements CauseHandler {

    public BitBucketPushCauseHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(BitBucketPushCause.class.getName());
    }

    @Override
    public boolean isSupported(@NonNull Cause cause) {
        return isBitBucketPushCause(cause);
    }

    protected boolean isBitBucketPushCause(Cause cause) {
        return cause instanceof BitBucketPushCause;
    }

    @Override
    public String getStructuredDescription(@NonNull Cause cause) {
        // https://github.com/jenkinsci/bitbucket-plugin/blob/master/src/main/java/com/cloudbees/jenkins/plugins/BitBucketPushCause.java#L33
        String id = cause.getShortDescription().replaceAll(".* by ", "");
        return cause.getClass().getSimpleName() + ":" + id;
    }
}
