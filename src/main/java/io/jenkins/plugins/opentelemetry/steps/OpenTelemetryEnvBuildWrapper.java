/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.steps;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.semconv.OTelEnvironmentVariablesConventions;
import jenkins.tasks.SimpleBuildWrapper;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * A simple build wrapper to contribute OpenTelemetry environment variables.
 */
public class OpenTelemetryEnvBuildWrapper extends SimpleBuildWrapper {
    private final static Logger LOGGER = Logger.getLogger(OpenTelemetryEnvBuildWrapper.class.getName());

    private JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration;

    private String passwordToMask;
    private boolean override;

    @DataBoundConstructor
    public OpenTelemetryEnvBuildWrapper() {
    }

    @DataBoundSetter
    public void setOverride(boolean override) {
        this.override = override;
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        Map<String, String> otelConfiguration = jenkinsOpenTelemetryPluginConfiguration.getOtelConfigurationAsEnvironmentVariables();
        for (Map.Entry<String, String> otelEnvironmentVariable : otelConfiguration.entrySet()) {
            String envVarValue = otelEnvironmentVariable.getValue();
            String envVarName = otelEnvironmentVariable.getKey();
            if (envVarValue != null) {
                // TODO: allow to override them option.
                if (context.getEnv().containsKey(envVarName)) {
                    LOGGER.log(Level.FINE, () -> "Overwrite environment variable '" + envVarName + "'");
                }
                context.env(envVarName, envVarValue);
            }
        }

        String header = otelConfiguration.get(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_HEADERS);
        if (header != null) {
            this.passwordToMask = header;
        }
    }

    @Override
    public ConsoleLogFilter createLoggerDecorator(Run<?, ?> build) {
        return new FilterImpl(passwordToMask);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("withOtelEnv")
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Binds OpenTelemetry Environment Variables";
        }
    }

    private static final class FilterImpl extends ConsoleLogFilter implements Serializable {
        private static final long serialVersionUID = 10L;

        private final String passwordToMask;

        FilterImpl(String passwordToMask) {
            this.passwordToMask = passwordToMask;
        }

        @Override
        public OutputStream decorateLogger(Run _ignore, OutputStream logger) throws IOException, InterruptedException {
            return new PasswordsMaskOutputStream(logger, passwordToMask);
        }
    }

    /** Similar to {@code MaskPasswordsOutputStream}. */
    public static final class PasswordsMaskOutputStream extends LineTransformationOutputStream {
        private static final String MASKED_PASSWORD = "******";
        private final OutputStream  logger;
        private final Pattern passwordsAsPattern;

        public PasswordsMaskOutputStream(OutputStream logger, String passwordToMask) {
            this.logger = logger;

            if (StringUtils.isNotEmpty(passwordToMask)) {
                StringBuilder regex = new StringBuilder().append('(');
                regex.append(Pattern.quote(passwordToMask));
                regex.append(')');
                this.passwordsAsPattern = Pattern.compile(regex.toString());
            } else{
                this.passwordsAsPattern = null;
            }
        }

        @Override
        protected void eol(byte[] bytes, int len) throws IOException {
            String line = new String(bytes, 0, len, StandardCharsets.UTF_8);
            if(passwordsAsPattern != null) {
                line = passwordsAsPattern.matcher(line).replaceAll(MASKED_PASSWORD);
            }
            logger.write(line.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration) {
        this.jenkinsOpenTelemetryPluginConfiguration = jenkinsOpenTelemetryPluginConfiguration;
    }
}