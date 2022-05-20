/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;

import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class LifecycleDebugger {
    private final static Logger logger = Logger.getLogger(LifecycleDebugger.class.getName());
    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED, before = InitMilestone.SYSTEM_CONFIG_ADAPTED)
    public void stage1(){
        logger.log(Level.SEVERE, "after = SYSTEM_CONFIG_LOADED, before = SYSTEM_CONFIG_ADAPTED");
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED, before = InitMilestone.JOB_LOADED)
    public void stage2(){
        logger.log(Level.SEVERE,"after = SYSTEM_CONFIG_ADAPTED, before = JOB_LOADED");
    }

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED, before = InitMilestone.COMPLETED)
    public void stage3(){
        logger.log(Level.SEVERE,"after = JOB_CONFIG_ADAPTED, before = COMPLETED");
    }
}
