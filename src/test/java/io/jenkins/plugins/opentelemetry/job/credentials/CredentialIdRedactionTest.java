/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.AbortException;
import java.util.Optional;
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException;
import org.junit.jupiter.api.Test;

class CredentialIdRedactionTest {

    @Test
    void theIdOfAMissingCredentialIsRedacted() {
        assertEquals(
                Optional.of("Could not find credentials entry with ID '<redacted>'"),
                CredentialIdRedaction.redactedMessage(
                        new CredentialNotFoundException("Could not find credentials entry with ID 'ze-id'")));
    }

    @Test
    void onlyTheIdIsRedactedWhenTheCredentialHasTheWrongType() {
        assertEquals(
                Optional.of("Credentials '<redacted>' is of type 'Secret text' where 'Username with password' was"
                        + " expected"),
                CredentialIdRedaction.redactedMessage(new CredentialNotFoundException(
                        "Credentials 'ze-id' is of type 'Secret text' where 'Username with password' was expected")));
    }

    @Test
    void anIdWithQuotesIsRedactedWhole() {
        assertEquals(
                Optional.of("Could not find credentials entry with ID '<redacted>'"),
                CredentialIdRedaction.redactedMessage(
                        new CredentialNotFoundException("Could not find credentials entry with ID 'ze-'id''")));
    }

    @Test
    void aMessageOfAnUnknownFormIsRedactedWhole() {
        assertEquals(
                Optional.of("<redacted>"),
                CredentialIdRedaction.redactedMessage(new CredentialNotFoundException("No 'ze-id' here")));
    }

    @Test
    void theMessageOfAMissingCredentialIsRedactedInTheMessageOfAWrapper() {
        CredentialNotFoundException cause =
                new CredentialNotFoundException("Could not find credentials entry with ID 'ze-id'");
        assertEquals(
                Optional.of("Binding failed in 'ze-step': Could not find credentials entry with ID '<redacted>'"),
                CredentialIdRedaction.redactedMessage(
                        new IllegalStateException("Binding failed in 'ze-step': " + cause.getMessage(), cause)));
    }

    @Test
    void otherErrorsAreNotRedacted() {
        assertEquals(
                Optional.empty(),
                CredentialIdRedaction.redactedMessage(new AbortException("Cannot invoke 'ze-method' on 'ze-object'")));
    }
}
