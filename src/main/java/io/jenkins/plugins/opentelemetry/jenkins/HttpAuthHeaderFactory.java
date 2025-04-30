package io.jenkins.plugins.opentelemetry.jenkins;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.tools.ant.types.CharSet;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor.FormException;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;

/**
 * Factory class to create HTTP authentication headers for Jenkins credentials.
 * This class is used to create authentication headers for different types of Jenkins credentials.
 * It supports API keys and basic authentication using username and password.
 * The class retrieves the credentials from Jenkins using the provided credentials ID.
 * It throws a CredentialsNotFoundException if the credentials are not found or if the credentials ID is not provided.
 */
public class HttpAuthHeaderFactory {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC_AUTH_FORMAT = "Basic %s";
    private static final String API_KEY_FORMAT = "ApiKey %s";
    private static final String BEARER_FORMAT = "Bearer %s";
    private static final String DIGEST_FORMAT_STRING = "%s:%s";

    private final Boolean bearerMode;
    private com.cloudbees.plugins.credentials.Credentials jenkinsCredentials;

    /**
     * Constructor to create an instance of HttpAuthHeaderFactory.
     *
     * @param jenkinsCredentialsId the ID of the Jenkins credentials
     */
    public HttpAuthHeaderFactory(String jenkinsCredentialsId) {
        this(jenkinsCredentialsId, false);
    }

    /**
     * Constructor to create an instance of HttpAuthHeaderFactory.
     *
     * @param jenkinsCredentials the Jenkins credentials
     */
    public HttpAuthHeaderFactory(com.cloudbees.plugins.credentials.Credentials jenkinsCredentials){
        this(jenkinsCredentials, false);
    }

    /**
     * Constructor to create an instance of HttpAuthHeaderFactory with bearer mode.
     *
     * @param jenkinsCredentials the Jenkins credentials
     * @param bearerMode         whether to use bearer mode for authentication
     */
    public HttpAuthHeaderFactory(com.cloudbees.plugins.credentials.Credentials jenkinsCredentials, Boolean bearerMode){
        this.jenkinsCredentials = jenkinsCredentials;
        this.bearerMode = bearerMode;
    }

    /**
     * Constructor to create an instance of HttpAuthHeaderFactory with bearer mode.
     *
     * @param jenkinsCredentialsId the ID of the Jenkins credentials
     * @param bearerMode           whether to use bearer mode for authentication
     */
    public HttpAuthHeaderFactory(String jenkinsCredentialsId, Boolean bearerMode) {
        jenkinsCredentials = getJenkinsCredentials(jenkinsCredentialsId);
        this.bearerMode = bearerMode;
    }

    /**
     * Gets the Jenkins credentials using the provided credentials ID.
     */
    private com.cloudbees.plugins.credentials.Credentials getJenkinsCredentials(String jenkinsCredentialsId) throws CredentialsNotFoundException {
        com.cloudbees.plugins.credentials.Credentials credentials = null;
        if (jenkinsCredentialsId == null || jenkinsCredentialsId.isEmpty()) {
            throw new CredentialsNotFoundException("No Jenkins credentials defined");
        }
        try {
            credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                    com.cloudbees.plugins.credentials.Credentials.class, Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
                    CredentialsMatchers.withId(jenkinsCredentialsId));
        } catch (NoSuchElementException e) {
            throw new CredentialsNotFoundException("No Jenkins credentials found for id '" + jenkinsCredentialsId + "'");
        }
        if (credentials == null) {
            throw new CredentialsNotFoundException("No Jenkins credentials found for id '" + jenkinsCredentialsId + "'");
        }
        return credentials;
    }

    /**
     * Creates an authentication header using the Jenkins credentials.
     * This method checks the type of credentials and creates the appropriate header.
     * It supports API key, Bearer, and basic authentication.
     *
     * @return the authentication header
     * @throws CredentialsNotFoundException if the credentials are not found
     */
    public Header createAuthHeader() throws CredentialsNotFoundException {
        if(jenkinsCredentials instanceof com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials usernamePassword) {
            return createAuthHeader(usernamePassword);
        } else if (jenkinsCredentials instanceof StringCredentials stringCredentials) {
            return createAuthHeader(stringCredentials);
        } else {
            throw new CredentialsNotFoundException("Incorrect credentials type, supported are StringCredentials and UsernamePasswordCredentials");
        }
    }

    /**
     * Creates an authentication header using the provided StringCredentials.
     * This method supports both API key and Bearer authentication.
     *
     * @param stringCredentials the StringCredentials to use for authentication
     * @return the authentication header
     */
    private Header createAuthHeader(StringCredentials stringCredentials) {
        String valueformat = bearerMode ? BEARER_FORMAT : API_KEY_FORMAT;
        String value = String.format(valueformat, stringCredentials.getSecret().getPlainText());
        return new BasicHeader(AUTHORIZATION, value);
    }

    /**
     * Creates an authentication header using the provided UsernamePasswordCredentials.
     * This method supports basic authentication.
     *
     * @param jenkinsCredentials the UsernamePasswordCredentials to use for authentication
     * @return the authentication header
     * @throws CredentialsNotFoundException if the credentials are not found
     */
    private Header createAuthHeader(@NonNull com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials jenkinsCredentials)
            throws CredentialsNotFoundException {
        if (jenkinsCredentials == null) {
            throw new CredentialsNotFoundException("No Jenkins credentials set");
        }
        String username = jenkinsCredentials.getUsername();
        String password = jenkinsCredentials.getPassword().getPlainText();
        String digest = Base64.getEncoder().encodeToString(String.format(DIGEST_FORMAT_STRING, username, password).getBytes(StandardCharsets.UTF_8));
        String value = String.format(BASIC_AUTH_FORMAT, digest);
        return new BasicHeader(AUTHORIZATION, value);
    }

    /**
     * Creates an authentication header Factory using the provided credentials ID.
     * This method returns an Optional object.
     * If the credentials ID is null or empty, it returns an empty Optional.
     * If the credentials ID is valid, it returns an Optional containing the HttpAuthHeaderFactory object.
     *
     * @param jenkinsCredentialsId the ID of the Jenkins credentials
     * @return an Optional containing the HttpAuthHeaderFactory object
     */
    @NonNull
    public static Optional<HttpAuthHeaderFactory> createFactory(String jenkinsCredentialsId) {
        Optional<HttpAuthHeaderFactory> ret = Optional.empty();
        if (jenkinsCredentialsId != null && !jenkinsCredentialsId.isEmpty()) {
            ret = Optional.of(new HttpAuthHeaderFactory(jenkinsCredentialsId));
        }
        return ret;
    }

    /**
     * Creates an authentication header Factory using the provided credentials ID Optional.
     * This method returns an Optional object.
     * If the credentials ID is null or empty, it returns an empty Optional.
     * If the credentials ID is valid, it returns an Optional containing the HttpAuthHeaderFactory object.
     *
     * @param jenkinsCredentialsId the ID of the Jenkins credentials
     * @return an Optional containing the HttpAuthHeaderFactory object
     */
    @NonNull
    public static Optional<HttpAuthHeaderFactory> createFactory(@NonNull Optional<String> jenkinsCredentialsId) {
        Optional<HttpAuthHeaderFactory> ret = Optional.empty();
        if(jenkinsCredentialsId.isPresent() && !jenkinsCredentialsId.get().isEmpty()) {
            ret = HttpAuthHeaderFactory.createFactory(jenkinsCredentialsId.get());
        }
        return ret;
    }

    /**
     * Creates an authentication header Factory using the provided username and password.
     * This method returns an Optional object.
     * If the username or password is null or empty, it returns an empty Optional.
     * If the username and password are valid, it returns an Optional containing the HttpAuthHeaderFactory object.
     *
     * @param username the username for authentication
     * @param password the password for authentication
     * @return an Optional containing the HttpAuthHeaderFactory object
     */
    @NonNull
    public static Optional<HttpAuthHeaderFactory> createFactoryUsernamePassword(String username, String password) {
        Optional<HttpAuthHeaderFactory> ret = Optional.empty();
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            try {
                com.cloudbees.plugins.credentials.Credentials credentials = new UsernamePasswordCredentialsImpl(
                        com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL,
                        UUID.randomUUID().toString(),
                        "temporarily created credentials",
                        username,
                        password);
                ret = Optional.of(new HttpAuthHeaderFactory(credentials));
            } catch (FormException e) {
                throw new CredentialsNotFoundException("No Jenkins credentials set", e);
            }
        }
        return ret;
    }

    /**
     * Creates an authentication header Factory using the provided API key.
     * This method returns an Optional object.
     * If the API key is null or empty, it returns an empty Optional.
     * If the API key is valid, it returns an Optional containing the HttpAuthHeaderFactory object.
     *
     * @param apiKey the API key for authentication
     * @return an Optional containing the HttpAuthHeaderFactory object
     */
    @NonNull
    public static Optional<HttpAuthHeaderFactory> createFactoryApikey(String apiKey) {
        Optional<HttpAuthHeaderFactory> ret = Optional.empty();
        if (apiKey != null && !apiKey.isEmpty()) {
            com.cloudbees.plugins.credentials.Credentials credentials = new StringCredentialsImpl(
                    com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL,
                    UUID.randomUUID().toString(),
                    "temporarily created credentials",
                    Secret.fromString(apiKey));
            ret = Optional.of(new HttpAuthHeaderFactory(credentials));
        }
        return ret;
    }

    /**
     * Creates an authentication header Factory using the provided Bearer token.
     * This method returns an Optional object.
     * If the Bearer token is null or empty, it returns an empty Optional.
     * If the Bearer token is valid, it returns an Optional containing the HttpAuthHeaderFactory object.
     *
     * @param bearerToken the Bearer token for authentication
     * @return an Optional containing the HttpAuthHeaderFactory object
     */
    @NonNull
    public static Optional<HttpAuthHeaderFactory> createFactoryBearer(String bearerToken) {
        Optional<HttpAuthHeaderFactory> ret = Optional.empty();
        if (bearerToken != null && !bearerToken.isEmpty()) {
            com.cloudbees.plugins.credentials.Credentials credentials = new StringCredentialsImpl(
                    com.cloudbees.plugins.credentials.CredentialsScope.GLOBAL,
                    UUID.randomUUID().toString(),
                    "temporarily created credentials",
                    Secret.fromString(bearerToken));
            ret = Optional.of(new HttpAuthHeaderFactory(credentials, true));
        }
        return ret;
    }

}
