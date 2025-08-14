/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.jenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.util.Secret;
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

@WithJenkins
class HttpAuthHeaderFactoryTest {

    private JenkinsRule j;
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "testpassword";
    private static final String TOKEN = "testtoken";

    @BeforeEach
    void beforeEach(JenkinsRule j) {
        this.j = j;
        this.j.timeout = 0;
    }

    private String createUsernamePasswordCredentials() throws Exception {
        String credentialsId = UUID.randomUUID().toString();
        Credentials credentials = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, credentialsId, "test", USERNAME, PASSWORD);
        Map<Domain, List<Credentials>> domainCredentialsMap =
                SystemCredentialsProvider.getInstance().getDomainCredentialsMap();
        domainCredentialsMap.put(Domain.global(), Collections.singletonList(credentials));
        return credentialsId;
    }

    private String createSecretStringCredentials() {
        String credentialsId = UUID.randomUUID().toString();
        Credentials credentials =
                new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId, "test", Secret.fromString(TOKEN));
        Map<Domain, List<Credentials>> domainCredentialsMap =
                SystemCredentialsProvider.getInstance().getDomainCredentialsMap();
        domainCredentialsMap.put(Domain.global(), Collections.singletonList(credentials));
        return credentialsId;
    }

    private String createBaseCredentials() {
        String credentialsId = UUID.randomUUID().toString();
        Credentials credentials = new BaseCredentials(CredentialsScope.GLOBAL);
        Map<Domain, List<Credentials>> domainCredentialsMap =
                SystemCredentialsProvider.getInstance().getDomainCredentialsMap();
        domainCredentialsMap.put(Domain.global(), Collections.singletonList(credentials));
        return credentialsId;
    }

    private String base64Digest() {
        return java.util.Base64.getEncoder()
                .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testCreateAuthHeader_UsernamePasswordCredentials() throws Exception{
        String credentialsId = createUsernamePasswordCredentials();
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentialsId);
        Header header = factory.createAuthHeader();

        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        String expectedValue = "Basic " + base64Digest();
        assertEquals(expectedValue, header.getValue());
    }

    @Test
    void testCreateAuthHeader_StringCredentials_ApiKey() {
        String credentialsId = createSecretStringCredentials();
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentialsId);
        Header header = factory.createAuthHeader();

        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("ApiKey " + TOKEN, header.getValue());
    }

    @Test
    void testCreateAuthHeader_StringCredentials_BearerToken() {
        String credentialsId = createSecretStringCredentials();
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentialsId, true);
        Header header = factory.createAuthHeader();

        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("Bearer " + TOKEN, header.getValue());
    }

    @Test
    void testCreateAuthHeader_CredentialsNotFound() {
        String credentialsId = "nonexistent-credentials";
        assertThrowsExactly(CredentialsNotFoundException.class, () -> new HttpAuthHeaderFactory(credentialsId));
    }

    @Test
    void testCreateAuthHeader_NoCredentialsId() {
        assertThrowsExactly(CredentialsNotFoundException.class, () -> new HttpAuthHeaderFactory((String) null));
    }

    @Test
    void testCreateAuthHeader_EmptyCredentialsId() {
        assertThrowsExactly(CredentialsNotFoundException.class, () -> new HttpAuthHeaderFactory(""));
    }

    @Test
    void testCreateAuthHeader_IncorrectCredentialsType() {
        String credentialsId = createBaseCredentials();
        assertThrowsExactly(CredentialsNotFoundException.class, () -> new HttpAuthHeaderFactory(credentialsId));
    }

    @Test
    void testCreateFactory_ValidCredentialsId() throws Exception {
        String credentialsId = createUsernamePasswordCredentials();

        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory(credentialsId);
        assertTrue(factory.isPresent());
        assertNotNull(factory.get().createAuthHeader());
    }

    @Test
    void testCreateFactory_NullCredentialsId() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory((String) null);
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactory_EmptyCredentialsId() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory("");
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactory_Optional_ValidCredentialsId() throws Exception {
        String credentialsId = createUsernamePasswordCredentials();

        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory(Optional.of(credentialsId));
        assertTrue(factory.isPresent());
        assertNotNull(factory.get().createAuthHeader());
    }

    @Test
    void testCreateFactory_Optional_EmptyCredentialsId() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactory(Optional.empty());
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactoryUsernamePassword_ValidCredentials() {
        Optional<HttpAuthHeaderFactory> factory =
                HttpAuthHeaderFactory.createFactoryUsernamePassword(USERNAME, PASSWORD);
        assertTrue(factory.isPresent());
        Header header = factory.get().createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        String expectedValue = "Basic " + base64Digest();
        assertEquals(expectedValue, header.getValue());
    }

    @Test
    void testCreateFactoryUsernamePassword_NullUsername() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryUsernamePassword(null, PASSWORD);
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactoryUsernamePassword_EmptyUsername() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryUsernamePassword("", PASSWORD);
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactoryUsernamePassword_NullPassword() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryUsernamePassword("testuser", null);
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactoryUsernamePassword_EmptyPassword() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryUsernamePassword("testuser", "");
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactoryApikey_ValidApiKey() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryApikey(TOKEN);
        assertTrue(factory.isPresent());
        Header header = factory.get().createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("ApiKey " + TOKEN, header.getValue());
    }

    @Test
    void testCreateFactoryApikey_NullApiKey() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryApikey(null);
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactoryApikey_EmptyApiKey() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryApikey("");
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactoryBearer_ValidBearerToken() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryBearer(TOKEN);
        assertTrue(factory.isPresent());
        Header header = factory.get().createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("Bearer " + TOKEN, header.getValue());
    }

    @Test
    void testCreateFactoryBearer_NullBearerToken() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryBearer(null);
        assertFalse(factory.isPresent());
    }

    @Test
    void testCreateFactoryBearer_EmptyBearerToken() {
        Optional<HttpAuthHeaderFactory> factory = HttpAuthHeaderFactory.createFactoryBearer("");
        assertFalse(factory.isPresent());
    }

    @Test
    void testConstructor_CredentialsObject_ApiKey() {
        StringCredentials credentials = new StringCredentialsImpl(
                CredentialsScope.GLOBAL, UUID.randomUUID().toString(), "test", Secret.fromString(TOKEN));
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentials);
        Header header = factory.createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("ApiKey " + TOKEN, header.getValue());
    }

    @Test
    void testConstructor_CredentialsObject_BearerToken() {
        StringCredentials credentials = new StringCredentialsImpl(
                CredentialsScope.GLOBAL, UUID.randomUUID().toString(), "test", Secret.fromString(TOKEN));
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentials, true);
        Header header = factory.createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        assertEquals("Bearer " + TOKEN, header.getValue());
    }

    @Test
    void testConstructor_CredentialsObject_UsernamePassword() throws Exception {
        UsernamePasswordCredentialsImpl credentials;
        credentials = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, UUID.randomUUID().toString(), "test", USERNAME, PASSWORD);
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentials);
        Header header = factory.createAuthHeader();
        assertNotNull(header);
        assertEquals("Authorization", header.getName());
        String expectedValue = "Basic " + base64Digest();
        assertEquals(expectedValue, header.getValue());
    }

    @Test
    void testConstructor_CredentialsObject_IncorrectCredentialsType() {
        Credentials credentials = new BaseCredentials(CredentialsScope.GLOBAL);
        HttpAuthHeaderFactory factory = new HttpAuthHeaderFactory(credentials);
        assertThrowsExactly(CredentialsNotFoundException.class, factory::createAuthHeader);
    }
}
