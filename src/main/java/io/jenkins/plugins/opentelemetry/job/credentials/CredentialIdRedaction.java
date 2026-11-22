/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.credentials;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes the credential ID from the message of the error that credentials-binding throws when a credential is
 * missing or has the wrong type. The error is recognized by its class name, so this needs no dependency on
 * credentials-binding. Only the ID is replaced: the rest of the message, and the text of other errors, is kept.
 */
public final class CredentialIdRedaction {

    static final String REDACTED = "<redacted>";

    private static final String CREDENTIAL_NOT_FOUND_EXCEPTION =
            "org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException";

    /**
     * The messages of {@code MultiBinding.getCredentials}. Group 1 is the ID. The match is greedy, so an ID that
     * contains quotes is replaced whole.
     */
    private static final List<Pattern> KNOWN_MESSAGES = List.of(
            Pattern.compile("^Could not find credentials entry with ID '(.*)'$", Pattern.DOTALL),
            Pattern.compile("^Credentials '(.*)' is of type '[^']*' where '[^']*' was expected$", Pattern.DOTALL));

    private CredentialIdRedaction() {}

    /**
     * @param throwable the error that a span records
     * @return the message of the error without the credential ID, if the error or one of its causes is the error of
     *     a missing credential; empty for any other error, whose message names no credential ID. A message of the
     *     credential error in a form that is not known is replaced whole, as the ID cannot be found in it.
     */
    @NonNull
    public static Optional<String> redactedMessage(@NonNull Throwable throwable) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = throwable; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (CREDENTIAL_NOT_FOUND_EXCEPTION.equals(cause.getClass().getName())) {
                return Optional.of(redact(throwable.getMessage(), cause.getMessage()));
            }
        }
        return Optional.empty();
    }

    /** The message of the credential error, redacted, in place of its text in the message of the error. */
    private static String redact(String message, String credentialMessage) {
        if (credentialMessage == null) {
            return message == null ? REDACTED : message;
        }
        String redacted = REDACTED;
        for (Pattern pattern : KNOWN_MESSAGES) {
            Matcher matcher = pattern.matcher(credentialMessage);
            if (matcher.matches()) {
                redacted = credentialMessage.substring(0, matcher.start(1))
                        + REDACTED
                        + credentialMessage.substring(matcher.end(1));
                break;
            }
        }
        return message == null || !message.contains(credentialMessage)
                ? redacted
                : message.replace(credentialMessage, redacted);
    }
}
