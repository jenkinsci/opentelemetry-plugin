package io.jenkins.plugins.opentelemetry.job;


import hudson.model.Action;

public class OpenTelemetryAction implements Action {
    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "OpenTelemetry";
    }

    @Override
    public String getUrlName() {
        return "opentelemetry";
    }
}
