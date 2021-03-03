/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    private String trustedCertificatesPem;

    private OtlpAuthentication authentication;

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
        this.endpoint = sanitizeOtlpEndpoint(this.endpoint);
        initializeOpenTelemetry();
        save();
        return true;
    }

    @PostConstruct
    public void initializeOpenTelemetry() {
        OpenTelemetryConfiguration newOpenTelemetryConfiguration = new OpenTelemetryConfiguration(this.getEndpoint(), this.getTrustedCertificatesPem(), this.getAuthentication());
        if (Objects.equal(this.currentOpenTelemetryConfiguration, newOpenTelemetryConfiguration)) {
            LOGGER.log(Level.FINE, "Configuration didn't change, skip reconfiguration");
            return;
        }
        openTelemetrySdkProvider.initialize(newOpenTelemetryConfiguration);
        this.currentOpenTelemetryConfiguration = newOpenTelemetryConfiguration;
    }

    /**
     *
     * @return {@code null} or endpoint URI prefixed by a protocol scheme ("http://", "https://"...)
     */
    @CheckForNull
    public String sanitizeOtlpEndpoint(@Nullable String grpcEndpoint) {
        if (Strings.isNullOrEmpty(grpcEndpoint)) {
            return null;
        } else if (grpcEndpoint.contains("://")) {
            return grpcEndpoint;
        } else {
            return "http://" + grpcEndpoint;
        }
    }

    /**
     * Never empty
     */
    @CheckForNull
    public String getEndpoint() {
        return sanitizeOtlpEndpoint(this.endpoint);
    }

    @DataBoundSetter
    public void setEndpoint(String endpoint) {
        this.endpoint = sanitizeOtlpEndpoint(endpoint);
    }

    @Nonnull
    public OtlpAuthentication getAuthentication() {
        return this.authentication == null ? new NoAuthentication() : this.authentication;
    }

    @DataBoundSetter
    public void setAuthentication(OtlpAuthentication authentication) {
        this.authentication = authentication;
    }

    @CheckForNull
    public String getTrustedCertificatesPem() {
        return trustedCertificatesPem;
    }

    @DataBoundSetter
    public void setTrustedCertificatesPem(String trustedCertificatesPem) {
        this.trustedCertificatesPem = trustedCertificatesPem;
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

    /**
     * For visualisation in config.jelly
     */
    @Nonnull
    public String getVisualisationObservabilityBackendsString(){
        return "Visualisation observability backends: " + ObservabilityBackend.allDescriptors().stream().sorted().map(d-> d.getDisplayName()).collect(Collectors.joining(", "));
    }
}
