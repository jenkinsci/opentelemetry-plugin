/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import jenkins.YesNoMaybe;

import java.util.logging.Level;
import java.util.logging.Logger;

@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class LifecycleDebugger {
    private final static Logger logger = Logger.getLogger(LifecycleDebugger.class.getName());
    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED, before = InitMilestone.SYSTEM_CONFIG_ADAPTED)
    public void before_stage_SYSTEM_CONFIG_ADAPTED(){
        logger.log(Level.FINE, "after = SYSTEM_CONFIG_LOADED, before = SYSTEM_CONFIG_ADAPTED");
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED, before = InitMilestone.JOB_LOADED)
    public void before_stage_JOB_LOADED(){
        logger.log(Level.FINE,"after = SYSTEM_CONFIG_ADAPTED, before = JOB_LOADED");
    }

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED, before = InitMilestone.COMPLETED)
    public void before_stage_COMPLETED(){
        logger.log(Level.FINE,"after = JOB_CONFIG_ADAPTED, before = COMPLETED");
    }
}
