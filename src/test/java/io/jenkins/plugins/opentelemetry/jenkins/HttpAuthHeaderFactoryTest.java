/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.jenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.hc.core5.http.Header;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.model.Descriptor.FormException;
import hudson.util.Secret;

@WithJenkins
public class HttpAuthHeaderFactoryTest {

    protected JenkinsRule j;
    private static String USERNAME = "testuser";
    private static String PASSWORD = "testpassword";
    private static String TOKEN = "testtoken";

    @BeforeEach
    void beforeEach(JenkinsRule j) {
        this.j = j;
        this.j.timeout = 0;
    }

    private String createUsernamePasswordCredentials() {
        String credentialsId = UUID.randomUUID().toString();
        try {
            Credentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialsId,
                    "test", USERNAME, PASSWORD);
            Map<Domain, List<Credentials>> domainCredentialsMap = SystemCredentialsProvider.getInstance()
                    .getDomainCredentialsMap();
            domainCredentialsMap.put(Domain.global(), Collections.singletonList(credentials));
        } catch (FormException e) {
            assertNull(e, "FormException should not be thrown");
        }
        return credentialsId;
    }

    private String createSecretStringCredentials() {
        String credentialsId = UUID.randomUUID().toString();
        Credentials credentials = new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "test",
                Secret.fromString(TOKEN));
        Map<Domain, List<Credentials>> domainCredentialsMap = SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap();
        domainCredentialsMap.put(Domain.global(), Collections.singletonList(credentials));
        return credentialsId;
    }

    private String createBaseCredentials() {
        String credentialsId = UUID.randomUUID().toString();
        Credentials credentials = new BaseCredentials(CredentialsScope.GLOBAL);
        Map<Domain, List<Credentials>> domainCredentialsMap = SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap();
        domainCredentialsMap.put(Domain.global(), Collections.singletonList(credentials));
        return credentialsId;
    }

    private String base64Digest(){
        return java.util.Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testCreateAuthHeader_UsernamePasswordCredentials() {
        String credentialsId = createUsernamePasswordCredentials();
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentialsId);
        Header header = factory.createAuthHeader();

        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        String expectedValue = "Basic " + base64Digest();
        assertEquals(expectedValue, header.getValue());
    }

    @Test
    public void testCreateAuthHeader_StringCredentials_ApiKey() {
        String credentialsId = createSecretStringCredentials();
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentialsId);
        Header header = factory.createAuthHeader();

        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("ApiKey " + TOKEN, header.getValue());
    }

    @Test
    public void testCreateAuthHeader_StringCredentials_BearerToken() {
        String credentialsId = createSecretStringCredentials();
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentialsId, true);
        Header header = factory.createAuthHeader();

        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("Bearer " + TOKEN, header.getValue());
    }

    @Test
    public void testCreateAuthHeader_CredentialsNotFound() {
        String credentialsId = "nonexistent-credentials";
        assertThrowsExactly(CredentialsNotFoundException.class, () -> new HttpAuthHeaderFactory(credentialsId));
    }

    @Test
    public void testCreateAuthHeader_NoCredentialsId() {
        assertThrowsExactly(CredentialsNotFoundException.class, () -> new HttpAuthHeaderFactory((String) null));
    }

    @Test
    public void testCreateAuthHeader_EmptyCredentialsId() {
        assertThrowsExactly(CredentialsNotFoundException.class, () -> new HttpAuthHeaderFactory(""));
    }

    @Test
    public void testCreateAuthHeader_IncorrectCredentialsType() {
        String credentialsId = createBaseCredentials();
        assertThrowsExactly(CredentialsNotFoundException.class, () -> new HttpAuthHeaderFactory(credentialsId));
    }

    @Test
    public void testCreateFactory_ValidCredentialsId() {
        String credentialsId = createUsernamePasswordCredentials();

        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory(credentialsId);
        assertTrue(factory.isPresent());
        assertNotNull(factory.get().createAuthHeader());
    }

    @Test
    public void testCreateFactory_NullCredentialsId() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory((String) null);
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactory_EmptyCredentialsId() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory("");
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactory_Optional_ValidCredentialsId() {
        String credentialsId = createUsernamePasswordCredentials();

        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory(Optional.of(credentialsId));
        assertTrue(factory.isPresent());
        assertNotNull(factory.get().createAuthHeader());
    }

    @Test
    public void testCreateFactory_Optional_EmptyCredentialsId() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory(Optional.empty());
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactoryUsernamePassword_ValidCredentials() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryUsernamePassword(USERNAME,
                PASSWORD);
        assertTrue(factory.isPresent());
        Header header = factory.get().createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        String expectedValue = "Basic " + base64Digest();
        assertEquals(expectedValue, header.getValue());
    }

    @Test
    public void testCreateFactoryUsernamePassword_NullUsername() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryUsernamePassword(null,
                PASSWORD);
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactoryUsernamePassword_EmptyUsername() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryUsernamePassword("",
                PASSWORD);
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactoryUsernamePassword_NullPassword() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryUsernamePassword("testuser", null);
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactoryUsernamePassword_EmptyPassword() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryUsernamePassword("testuser", "");
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactoryApikey_ValidApiKey() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryApikey(TOKEN);
        assertTrue(factory.isPresent());
        Header header = factory.get().createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("ApiKey " + TOKEN, header.getValue());
    }

    @Test
    public void testCreateFactoryApikey_NullApiKey() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryApikey(null);
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactoryApikey_EmptyApiKey() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryApikey("");
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactoryBearer_ValidBearerToken() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryBearer(TOKEN);
        assertTrue(factory.isPresent());
        Header header = factory.get().createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("Bearer " + TOKEN, header.getValue());
    }

    @Test
    public void testCreateFactoryBearer_NullBearerToken() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryBearer(null);
        assertFalse(factory.isPresent());
    }

    @Test
    public void testCreateFactoryBearer_EmptyBearerToken() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryBearer("");
        assertFalse(factory.isPresent());
    }

    @Test
    public void testConstructor_CredentialsObject_ApiKey() {
        StringCredentials credentials = new StringCredentialsImpl(CredentialsScope.GLOBAL, UUID.randomUUID().toString(),
                "test", Secret.fromString(TOKEN));
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentials);
        Header header = factory.createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("ApiKey " + TOKEN, header.getValue());
    }

    @Test
    public void testConstructor_CredentialsObject_BearerToken() {
        StringCredentials credentials = new StringCredentialsImpl(CredentialsScope.GLOBAL, UUID.randomUUID().toString(),
                "test", Secret.fromString(TOKEN));
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentials, true);
        Header header = factory.createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("Bearer " + TOKEN, header.getValue());
    }

    @Test
    public void testConstructor_CredentialsObject_UsernamePassword() {
        UsernamePasswordCredentialsImpl credentials;
        try {
            credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                    UUID.randomUUID().toString(), "test", USERNAME, PASSWORD);
            HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentials);
            Header header = factory.createAuthHeader();
            assertNotNull(header);
            assertEquals("Authorization", header.getName());
            String expectedValue = "Basic " + base64Digest();
            assertEquals(expectedValue, header.getValue());
        } catch (FormException e) {
            assertNull(e, "FormException should not be thrown");
        }
    }

    @Test
    public void testConstructor_CredentialsObject_IncorrectCredentialsType() {
        Credentials credentials = new BaseCredentials(CredentialsScope.GLOBAL);
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentials);
        assertThrowsExactly(CredentialsNotFoundException.class, () -> factory.createAuthHeader());
    }
}
