/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import groovy.lang.Writable;
import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
@Symbol("openTelemetry")
public class JenkinsOpenTelemetryPluginConfiguration extends GlobalConfiguration {
    private final static Logger LOGGER = Logger.getLogger(JenkinsOpenTelemetryPluginConfiguration.class.getName());

    private String endpoint;
    private boolean useTls;
    private String authenticationTokenName;
    private String authenticationTokenValueId;
    private String traceVisualisationBaseUrl;
    private String traceVisualisationUrlTemplate;
    private transient Template traceVisualisationUrlGTemplate;

    private transient OpenTelemetrySdkProvider openTelemetrySdkProvider;

    /**
     * The previously used configuration. Kept in memory to prevent unneeded reconfigurations.
     */
    protected transient OpenTelemetryConfiguration currentOpenTelemetryConfiguration;

    @DataBoundConstructor
    public JenkinsOpenTelemetryPluginConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        super.configure(req, json);
        initializeOpenTelemetry();
        save();
        return true;
    }

    @PostConstruct
    public void initializeOpenTelemetry() {
        OpenTelemetryConfiguration newOpenTelemetryConfiguration = new OpenTelemetryConfiguration(this.endpoint, this.useTls, this.authenticationTokenName, this.authenticationTokenValueId);
        if (Objects.equal(this.currentOpenTelemetryConfiguration, newOpenTelemetryConfiguration)) {
            LOGGER.log(Level.INFO, "Configuration didn't change, skip reconfiguration");
            return;
        }

        if (this.endpoint == null) {
            openTelemetrySdkProvider.initializeNoOp();
        } else {
            String authenticationTokenValue;
            if (Strings.isNullOrEmpty(this.authenticationTokenValueId)) {
                authenticationTokenValue = null;
            } else {
                StringCredentials credentials = (StringCredentials) CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentials(StringCredentials.class, Jenkins.get(),
                                ACL.SYSTEM, Collections.EMPTY_LIST),
                        CredentialsMatchers.withId(this.authenticationTokenValueId));
                if (credentials == null) {
                    LOGGER.log(Level.WARNING, () -> "StringCredentials with id `" + authenticationTokenValueId + "` not found. Fall back to empty secret, an authentication error is likely to happen.");
                    authenticationTokenValue = "";
                } else {
                    authenticationTokenValue = Secret.toString(credentials.getSecret());
                }
            }
            openTelemetrySdkProvider.initializeForGrpc(this.endpoint, this.useTls, this.authenticationTokenName, authenticationTokenValue);
        }
        this.currentOpenTelemetryConfiguration = newOpenTelemetryConfiguration;
    }

    public String getEndpoint() {
        return endpoint;
    }

    @DataBoundSetter
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public boolean isUseTls() {
        return useTls;
    }

    @DataBoundSetter
    public void setUseTls(boolean useTls) {
        this.useTls = useTls;
    }

    public String getAuthenticationTokenName() {
        return authenticationTokenName;
    }

    @DataBoundSetter
    public void setAuthenticationTokenName(String authenticationTokenName) {
        this.authenticationTokenName = authenticationTokenName;
    }

    public String getAuthenticationTokenValueId() {
        return authenticationTokenValueId;
    }

    @DataBoundSetter
    public void setAuthenticationTokenValueId(String authenticationTokenValueId) {
        this.authenticationTokenValueId = authenticationTokenValueId;
    }

    public String getTraceVisualisationBaseUrl() {
        return traceVisualisationBaseUrl;
    }

    @DataBoundSetter
    public void setTraceVisualisationBaseUrl(String traceVisualisationBaseUrl) {
        this.traceVisualisationBaseUrl = traceVisualisationBaseUrl;
    }

    public String getTraceVisualisationUrlTemplate() {
        return traceVisualisationUrlTemplate;
    }

    /**
     * @return {@code null} if no {@link #traceVisualisationUrlTemplate} has been defined or if the   {@link #traceVisualisationUrlTemplate} has a syntax error
     */
    @CheckForNull
    public Template getTraceVisualisationUrlGTemplate() {
        if(this.traceVisualisationUrlTemplate == null || this.traceVisualisationUrlTemplate.isEmpty()) {
            return null;
        }
        if (this.traceVisualisationUrlGTemplate == null) {

            GStringTemplateEngine gStringTemplateEngine = new GStringTemplateEngine();
            try {
                this.traceVisualisationUrlGTemplate = gStringTemplateEngine.createTemplate(this.traceVisualisationUrlTemplate);
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.log(Level.WARNING, "Invalid Trace Visualisation URL Template '" + this.traceVisualisationUrlTemplate + "'", e);
                this.traceVisualisationUrlGTemplate = ERROR_TEMPLATE;
            }
        }
        return traceVisualisationUrlGTemplate == ERROR_TEMPLATE ? null : traceVisualisationUrlGTemplate;
    }

    @DataBoundSetter
    public void setTraceVisualisationUrlTemplate(String traceVisualisationUrlTemplate) {
        if (!Objects.equal(this.traceVisualisationUrlTemplate, traceVisualisationUrlTemplate)) {
            this.traceVisualisationUrlTemplate = traceVisualisationUrlTemplate;
            this.traceVisualisationUrlGTemplate = null;
        }
    }

    @Inject
    public void setOpenTelemetrySdkProvider(OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.openTelemetrySdkProvider = openTelemetrySdkProvider;
    }

    public ListBoxModel doFillAuthenticationTokenValueIdItems() {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return new StandardListBoxModel().includeCurrentValue(this.authenticationTokenValueId);
        }
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                        ACL.SYSTEM,
                        Jenkins.get(),
                        StringCredentials.class,
                        Collections.<DomainRequirement>emptyList(),
                        CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StringCredentials.class)))
                .includeCurrentValue(authenticationTokenValueId);
    }


    public final static Template ERROR_TEMPLATE = new Template() {
        @Override
        public Writable make() {
            return out -> {
                out.write("#ERROR#");
                return out;
            };
        }

        @Override
        public Writable make(Map binding) {
            return out -> {
                out.write("#ERROR#");
                return out;
            };
        }
    };
}
