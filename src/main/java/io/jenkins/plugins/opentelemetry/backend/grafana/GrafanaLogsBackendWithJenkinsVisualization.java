/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.GrafanaBackend;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.jenkins.CredentialsNotFoundException;
import io.jenkins.plugins.opentelemetry.jenkins.JenkinsCredentialsToApacheHttpCredentialsAdapter;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.opentelemetry.api.OpenTelemetry;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.http.auth.Credentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class GrafanaLogsBackendWithJenkinsVisualization extends GrafanaLogsBackend implements TemplateBindingsProvider {
    private final static String MSG_LOKI_URL_IS_BLANK = "Loki URL is blank, logs will not be stored in Elasticsearch";
    private final static Logger logger = Logger.getLogger(GrafanaLogsBackendWithJenkinsVisualization.class.getName());

    private String grafanaLokiDatasourceIdentifier = GrafanaBackend.DEFAULT_LOKI_DATA_SOURCE_IDENTIFIER;

    private String lokiUrl;
    private boolean disableSslVerifications;
    private String lokiCredentialsId;
    private String lokiTenantId;

    @DataBoundConstructor
    public GrafanaLogsBackendWithJenkinsVisualization() {

    }

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
        OpenTelemetry openTelemetry = OpenTelemetrySdkProvider.get().getOpenTelemetry();

        String serviceName = templateBindingsProvider.getBindings().get(ObservabilityBackend.TemplateBindings.SERVICE_NAME).toString();
        Optional<String> serviceNamespace = Optional.ofNullable(templateBindingsProvider.getBindings().get(ObservabilityBackend.TemplateBindings.SERVICE_NAMESPACE)).map(Object::toString);
        Optional<String> lokiTenantId = Optional.ofNullable(this.lokiTenantId).filter(StringUtils::isNotBlank);
        return new LokiLogStorageRetriever(
            lokiUrl,
            disableSslVerifications,
            getLokiApacheHttpCredentials(lokiCredentialsId), lokiTenantId,
            getBuildLogsVisualizationUrlTemplate(),
            TemplateBindingsProvider.compose(templateBindingsProvider, this.getBindings()),
            serviceName,
            serviceNamespace,
            openTelemetry);
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

    /**
     *
     * @param lokiCredentialsId Jenkins credentials id
     */
    @NonNull
    protected static Optional<Credentials> getLokiApacheHttpCredentials(@Nullable String lokiCredentialsId) {
        return Optional.ofNullable(lokiCredentialsId).filter(StringUtils::isNotBlank).map(JenkinsCredentialsToApacheHttpCredentialsAdapter::new);
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
        return "GrafanaLogsBackendWithJenkinsVisualization{" +
            "grafanaLokiDatasourceIdentifier='" + grafanaLokiDatasourceIdentifier + '\'' +
            ", lokiUrl='" + lokiUrl + '\'' +
            ", disableSslVerifications=" + disableSslVerifications +
            ", lokiCredentialsId='" + lokiCredentialsId + '\'' +
            '}';
    }

    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
            GrafanaBackend.TemplateBindings.GRAFANA_LOKI_DATASOURCE_IDENTIFIER, getGrafanaLokiDatasourceIdentifier());
    }

    @Extension(ordinal = 50)
    public static class DescriptorImpl extends GrafanaLogsBackend.DescriptorImpl {

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

        public ListBoxModel doFillLokiCredentialsIdItems(Item context, @QueryParameter String lokiCredentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                || context != null && !context.hasPermission(context.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            return new StandardListBoxModel().includeEmptyValue()
                .includeAs(ACL.SYSTEM, context, StandardUsernameCredentials.class)
                .includeCurrentValue(lokiCredentialsId);
        }

        @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT",
            justification = "We don't care about the return value, we just want to check that the credentials are valid")
        public FormValidation doCheckLokiCredentialsId(Item context, @QueryParameter String lokiCredentialsId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                || context != null && !context.hasPermission(context.CONFIGURE)) {
                return FormValidation.ok();
            }

            if (lokiCredentialsId == null || lokiCredentialsId.isEmpty()) {
                return FormValidation.ok(); // support anonymous access
            }
            try {
                new JenkinsCredentialsToApacheHttpCredentialsAdapter(lokiCredentialsId)
                    .getUserPrincipal().getName();
            } catch (CredentialsNotFoundException e) {
                return FormValidation.error("Loki credentials are not valid: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        public FormValidation doValidate(@QueryParameter String lokiUrl,
                                         @QueryParameter boolean disableSslVerifications, @QueryParameter String lokiCredentialsId,
                                         @QueryParameter String lokiTenantId) {
            FormValidation lokiUrlValidation = doCheckLokiUrl(lokiUrl);
            if (lokiUrlValidation.kind != FormValidation.Kind.OK) {
                return lokiUrlValidation;
            }
            OpenTelemetry openTelemetry = OpenTelemetrySdkProvider.get().getOpenTelemetry();
            try (LokiLogStorageRetriever lokiLogStorageRetriever = new LokiLogStorageRetriever(
                lokiUrl,
                disableSslVerifications,
                getLokiApacheHttpCredentials(lokiCredentialsId),
                Optional.ofNullable(lokiTenantId).filter(Predicate.not(String::isBlank)),
                ObservabilityBackend.ERROR_TEMPLATE,
                TemplateBindingsProvider.empty(),
                "##not-needed-to-invoke-check-loki-setup##",
                Optional.empty(),
                openTelemetry)) {
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
