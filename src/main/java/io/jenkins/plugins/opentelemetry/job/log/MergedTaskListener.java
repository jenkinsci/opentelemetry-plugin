package io.jenkins.plugins.opentelemetry.job.log;

import hudson.model.TaskListener;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.io.PrintStream;

public class MergedTaskListener implements TaskListener {

    final TaskListener main;
    final TaskListener secondary;

    public MergedTaskListener(TaskListener main, TaskListener secondary) {
        this.main = main;
        this.secondary = secondary;
    }

    @NonNull
    @Override
    public PrintStream getLogger() {
        try {
            return new MergedPrintStream(main.getLogger(), secondary.getLogger());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
