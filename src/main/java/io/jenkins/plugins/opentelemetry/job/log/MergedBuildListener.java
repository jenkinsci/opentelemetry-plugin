package io.jenkins.plugins.opentelemetry.job.log;

import hudson.model.BuildListener;
import hudson.model.TaskListener;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;

public class MergedBuildListener implements BuildListener {

    final TaskListener main;
    final TaskListener secondary;

    public MergedBuildListener(TaskListener main, TaskListener secondary) {
        this.main = main;
        this.secondary = secondary;
    }

    @NotNull
    @Override
    public PrintStream getLogger() {
        return new MergedPrintStream(main.getLogger(), secondary.getLogger());
    }
}

