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
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
@Symbol("openTelemetry")
public class JenkinsOpenTelemetryPluginConfiguration extends GlobalConfiguration {
    private final static Logger LOGGER = Logger.getLogger(JenkinsOpenTelemetryPluginConfiguration.class.getName());

    private String endpoint;
    private boolean useTls;
    private String authenticationTokenName;
    private String authenticationTokenValueId;

    private List<ObservabilityBackend> observabilityBackends = new ArrayList<>();

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
        req.bindJSON(this, json);
        // stapler oddity, empty lists coming from the HTTP request are not set on bean by  "req.bindJSON(this, json)"
        this.observabilityBackends = req.bindJSONToList(ObservabilityBackend.class, json.get("observabilityBackends"));
        initializeOpenTelemetry();
        save();
        return true;
    }

    @PostConstruct
    public void initializeOpenTelemetry() {
        OpenTelemetryConfiguration newOpenTelemetryConfiguration = new OpenTelemetryConfiguration(this.endpoint, this.useTls, this.authenticationTokenName, this.authenticationTokenValueId);
        if (Objects.equal(this.currentOpenTelemetryConfiguration, newOpenTelemetryConfiguration)) {
            LOGGER.log(Level.FINE, "Configuration didn't change, skip reconfiguration");
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

    @CheckForNull
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

    @CheckForNull
    public String getAuthenticationTokenName() {
        return authenticationTokenName;
    }

    @DataBoundSetter
    public void setAuthenticationTokenName(String authenticationTokenName) {
        this.authenticationTokenName = authenticationTokenName;
    }

    @CheckForNull
    public String getAuthenticationTokenValueId() {
        return authenticationTokenValueId;
    }

    @DataBoundSetter
    public void setAuthenticationTokenValueId(String authenticationTokenValueId) {
        this.authenticationTokenValueId = authenticationTokenValueId;
    }

    @DataBoundSetter
    public void setObservabilityBackends(List<ObservabilityBackend> observabilityBackends) {
        this.observabilityBackends = Optional.of(observabilityBackends).orElse(Collections.emptyList());
    }

    @Nonnull
    public List<ObservabilityBackend> getObservabilityBackends() {
        if (observabilityBackends == null) {
            observabilityBackends = new ArrayList<>();
        }
        return observabilityBackends;
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

    /**
     * For visualisation in config.jelly
     */
    @Nonnull
    public String getVisualisationObservabilityBackendsString(){
        return "Visualisation observability backends: " + ObservabilityBackend.allDescriptors().stream().sorted().map(d-> d.getDisplayName()).collect(Collectors.joining(", "));
    }
}
