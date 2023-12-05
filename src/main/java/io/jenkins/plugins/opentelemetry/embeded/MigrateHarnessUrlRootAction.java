package io.jenkins.plugins.opentelemetry.embeded;

import hudson.Extension;
import hudson.model.RootAction;

@Extension
public class MigrateHarnessUrlRootAction implements RootAction {

    @Override
    public String getIconFileName() {
        return "/plugin/opentelemetry/images/48x48/harness-logo.png";
    }

    @Override
    public String getDisplayName() {
        return "Migrate To Harness";
    }

    @Override
    public String getUrlName() {
        return "";
    }

}
