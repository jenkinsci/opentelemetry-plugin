/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConsoleNotesTest {
    @Test
    public void test1() {
        String expectedMessage = "[Pipeline] }";
        String data =
                "\u001B[8mha:////4M6NtB0GTRQCAdaplVIR0VJ+LHnCL5SK5Up3VN+g96s2AAAAoh+LCAAAAAAAAP9tjTEOAiEURD9rLGwtPQTbGRNjZUtoPAGyiLDkfxZYdytP5NW8g8RNrJxkknnTvNcb1jnBiZLl3mDvMGvHYxhtXXyi1N8CTdzTlWvCTMFwaSZJnTkvKKkYWMIaWAnYGNSBskNbYCu8eqg2KLTtpaT6HQU0rhvgCUxUc1GpfGFOsLuPXSb8ef4KYI6xADvU7j9Dg2gqvAAAAA==\u001B[0m[Pipeline] }";
        verifyParsing(expectedMessage, data);
    }

    @Test
    public void test2() {
        String expectedMessage = "[Pipeline] withEnv";
        String data =
                "\u001B[8mha:////4NtlmQKo1G0NaSfxFKN2g+kGotqT+iGehz/XCBJWEHlfAAAAph+LCAAAAAAAAP9tjTEOwjAQBM9BKWgpeYQDEh2iorXc8AITG+PEugv2haTiRXyNPxCIRMVWOyut5vmCMic4UPKycdgGzHWQXez91ORAqb1EGmRDZ1kTZopOajdosu44oyZ2MEcUsFCwdFhHygE9w0o15m6qaNBXJ07TtldQBHuDBwg1mdkk/sKYYH3tbSb8ef4KYOwYxI6h2G4+x/INtuQqUcEAAAA=\u001B[0m[Pipeline] withEnv";
        verifyParsing(expectedMessage, data);
    }

    @Test
    public void test3() {
        String expectedMessage = "Connecting to https://api.github.com using github";
        String data =
                "[8mha:////4Mvxbm1S/M3MEIZ30oxOtJ5Yv0tMJ+nki3DSqJQODl2EAAAAhB+LCAAAAAAAAP9b85aBtbiIwSa/KF0vKzUvOzOvODlTryCnNB3I0kvPLMkoTYpPKkrMS86IL84vLUpO1XPPLPEoTXLOzyvOz0n1yy9JZYAARiYGRi8GzpLM3NTiksTcgooiBqmM0pTi/Dy9ZIhiPayaGCoKgHRd5uufMwBru/q/jgAAAA==[0mConnecting to https://api.github.com using github";
        verifyParsing(expectedMessage, data);
    }

    private void verifyParsing(String expectedMessage, String data) {
        byte[] dataAsBytes = data.getBytes(StandardCharsets.UTF_8);
        ConsoleNotes.TextAndAnnotations textAndAnnotations = ConsoleNotes.parse(dataAsBytes, dataAsBytes.length);
        // attributes.asMap().forEach((k, v) -> System.out.println(k + ": " + v));
        String actualMessage = textAndAnnotations.text;
        Assertions.assertEquals(expectedMessage, actualMessage);
    }
}
