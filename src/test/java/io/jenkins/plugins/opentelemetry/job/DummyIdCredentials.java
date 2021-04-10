package io.jenkins.plugins.opentelemetry.job;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Originally from https://github.com/jenkinsci/credentials-plugin/blob/master/src/test/java/com/cloudbees/plugins/credentials/impl/DummyIdCredentials.java
 */
public class DummyIdCredentials extends BaseStandardCredentials implements UsernamePasswordCredentials {

    private final String username;

    private final Secret password;

    @DataBoundConstructor
    public DummyIdCredentials(String id, CredentialsScope scope, String username, String password, String description) {
        super(scope, id, description);
        this.username = username;
        this.password = Secret.fromString(password);
    }

    public String getUsername() {
        return username;
    }

    @NonNull
    public Secret getPassword() {
        return password;
    }

    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {

        public DescriptorImpl() {
            super();
        }

        @Override
        public String getDisplayName() {
            return "Dummy Id Credentials";
        }
    }
}
