/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.GrafanaBackend;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.jenkins.CredentialsNotFoundException;
import io.jenkins.plugins.opentelemetry.jenkins.HttpAuthHeaderFactory;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class GrafanaLogsBackendWithJenkinsVisualization extends GrafanaLogsBackend implements TemplateBindingsProvider {
    private static final String MSG_LOKI_URL_IS_BLANK = "Loki URL is blank, logs will not be stored in Elasticsearch";

    private String grafanaLokiDatasourceIdentifier = GrafanaBackend.DEFAULT_LOKI_DATA_SOURCE_IDENTIFIER;

    private String lokiUrl;
    private boolean disableSslVerifications;
    private String lokiCredentialsId;
    private String lokiTenantId;

    @DataBoundConstructor
    public GrafanaLogsBackendWithJenkinsVisualization() {}

    public String getGrafanaLokiDatasourceIdentifier() {
        return grafanaLokiDatasourceIdentifier;
    }

    @DataBoundSetter
    public void setGrafanaLokiDatasourceIdentifier(String grafanaLokiDatasourceIdentifier) {
        this.grafanaLokiDatasourceIdentifier = grafanaLokiDatasourceIdentifier;
    }

    @Override
    @MustBeClosed
    public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
        if (StringUtils.isBlank(lokiUrl)) {
            throw new IllegalStateException(MSG_LOKI_URL_IS_BLANK);
        }

        String serviceName = templateBindingsProvider
                .getBindings()
                .get(ObservabilityBackend.TemplateBindings.SERVICE_NAME)
                .toString();
        Optional<String> serviceNamespace = Optional.ofNullable(templateBindingsProvider
                        .getBindings()
                        .get(ObservabilityBackend.TemplateBindings.SERVICE_NAMESPACE))
                .map(Object::toString);
        Optional<String> lokiTenantId = Optional.ofNullable(this.lokiTenantId).filter(StringUtils::isNotBlank);

        return new LokiLogStorageRetriever(
                lokiUrl,
                disableSslVerifications,
                HttpAuthHeaderFactory.createFactory(lokiCredentialsId),
                lokiTenantId,
                getBuildLogsVisualizationUrlTemplate(),
                TemplateBindingsProvider.compose(templateBindingsProvider, this.getBindings()),
                serviceName,
                serviceNamespace);
    }

    public String getLokiUrl() {
        return lokiUrl;
    }

    @DataBoundSetter
    public void setLokiUrl(String lokiUrl) {
        this.lokiUrl = lokiUrl;
    }

    public boolean isDisableSslVerifications() {
        return disableSslVerifications;
    }

    @DataBoundSetter
    public void setDisableSslVerifications(boolean disableSslVerifications) {
        this.disableSslVerifications = disableSslVerifications;
    }

    public String getLokiTenantId() {
        return lokiTenantId;
    }

    @DataBoundSetter
    public void setLokiTenantId(String lokiTenantId) {
        this.lokiTenantId = lokiTenantId;
    }

    public String getLokiCredentialsId() {
        return lokiCredentialsId;
    }

    @DataBoundSetter
    public void setLokiCredentialsId(String lokiCredentialsId) {
        this.lokiCredentialsId = lokiCredentialsId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GrafanaLogsBackendWithJenkinsVisualization that = (GrafanaLogsBackendWithJenkinsVisualization) o;
        return Objects.equals(grafanaLokiDatasourceIdentifier, that.grafanaLokiDatasourceIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(grafanaLokiDatasourceIdentifier);
    }

    @Override
    public String toString() {
        return "GrafanaLogsBackendWithJenkinsVisualization{" + "grafanaLokiDatasourceIdentifier='"
                + grafanaLokiDatasourceIdentifier + '\'' + ", lokiUrl='"
                + lokiUrl + '\'' + ", disableSslVerifications="
                + disableSslVerifications + ", lokiCredentialsId='"
                + lokiCredentialsId + '\'' + '}';
    }

    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
                GrafanaBackend.TemplateBindings.GRAFANA_LOKI_DATASOURCE_IDENTIFIER,
                getGrafanaLokiDatasourceIdentifier());
    }

    @Extension(ordinal = 50)
    public static class DescriptorImpl extends GrafanaLogsBackend.DescriptorImpl {

        @RequirePOST
        public FormValidation doCheckLokiUrl(@QueryParameter("lokiUrl") String url) {
            if (StringUtils.isEmpty(url)) {
                return FormValidation.ok();
            }
            try {
                new URI(url).toURL();
            } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
                return FormValidation.error("Invalid Loki URL: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public ListBoxModel doFillLokiCredentialsIdItems(Item context, @QueryParameter String lokiCredentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    || context != null && !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }
            Jenkins jenkins = Jenkins.get();
            return new StandardUsernameListBoxModel()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            jenkins,
                            StandardUsernameCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(StandardUsernameCredentials.class))
                    .includeCurrentValue(lokiCredentialsId);
        }

        @RequirePOST
        public FormValidation doCheckLokiCredentialsId(Item context, @QueryParameter String lokiCredentialsId) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    || context != null && !context.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            if (lokiCredentialsId == null || lokiCredentialsId.isEmpty()) {
                return FormValidation.ok(); // support anonymous access
            }
            try {
                new HttpAuthHeaderFactory(lokiCredentialsId).createAuthHeader();
            } catch (CredentialsNotFoundException e) {
                return FormValidation.error("Loki credentials are not valid: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doValidate(
                @QueryParameter String lokiUrl,
                @QueryParameter boolean disableSslVerifications,
                @QueryParameter String lokiCredentialsId,
                @QueryParameter String lokiTenantId) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            FormValidation lokiUrlValidation = doCheckLokiUrl(lokiUrl);
            if (lokiUrlValidation.kind != FormValidation.Kind.OK) {
                return lokiUrlValidation;
            }
            try (LokiLogStorageRetriever lokiLogStorageRetriever = new LokiLogStorageRetriever(
                    lokiUrl,
                    disableSslVerifications,
                    HttpAuthHeaderFactory.createFactory(lokiCredentialsId),
                    Optional.ofNullable(lokiTenantId).filter(Predicate.not(String::isBlank)),
                    ObservabilityBackend.ERROR_TEMPLATE,
                    TemplateBindingsProvider.empty(),
                    "##not-needed-to-invoke-check-loki-setup##",
                    Optional.empty())) {
                return FormValidation.aggregate(lokiLogStorageRetriever.checkLokiSetup());
            } catch (NoSuchElementException e) {
                return FormValidation.error("No credentials found for id '" + lokiCredentialsId + "'");
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }

        @NonNull
        public String getDefaultLokiDataSourceIdentifier() {
            return GrafanaBackend.DEFAULT_LOKI_DATA_SOURCE_IDENTIFIER;
        }

        @Override
        public String getDefaultLokiOTelLogFormat() {
            return LokiOTelLogFormat.LOKI_V2_JSON_OTEL_FORMAT.name();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Store pipeline logs In Loki and visualize logs both in Grafana and through Jenkins ";
        }
    }
}
