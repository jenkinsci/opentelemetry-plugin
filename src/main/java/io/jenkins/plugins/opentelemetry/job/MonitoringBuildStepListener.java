package io.jenkins.plugins.opentelemetry.job;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.BuildStepListener;
import hudson.tasks.BuildStep;

public class MonitoringBuildStepListener extends BuildStepListener  {
    @Override
    public void started(AbstractBuild build, BuildStep bs, BuildListener listener) {

    }

    @Override
    public void finished(AbstractBuild build, BuildStep bs, BuildListener listener, boolean canContinue) {

    }
}
