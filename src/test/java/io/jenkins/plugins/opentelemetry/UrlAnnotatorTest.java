/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.MarkupText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.console.ConsoleAnnotator;
import hudson.console.UrlAnnotator;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class UrlAnnotatorTest {

    @Test
    public void test() {
        final ConsoleAnnotator consoleAnnotator = new UrlAnnotator().newInstance(null);

        List<String> tests = Arrays.asList(/*"http://example.com", "[my-example](https://example.com)",*/
            "[View logs in < href=\"https://e1c29b6b6a564b1c9bbdfaffc6ea184c.europe-west1.gcp.cloud.es.io:9243/app/logs/stream?logPosition=(end:now,start:now-1d,streamLive:!f)&logFilter=(language:kuery,query:%27trace.id:8ab25bae32e68c81d4043522096b5ac7%27)&\" target=\"_blank\">Elastic Observability]");

        for (String test : tests) {
            MarkupText text = new MarkupText(test);
            consoleAnnotator.annotate(null, text);
            System.out.println(consoleAnnotator);
            System.out.println(test);
            System.out.println("-> \t" + text);
        }
    }

    @Test
    public void testConsoleAnnotationOutputStream() throws Exception {
        Writer writer = new PrintWriter(System.out);
        final String firstToken = "View logs in ";
        final String secondToken = "Elastic Observability";
        final String url = "http://example.com";

        ConsoleAnnotator annotator = new ConsoleAnnotator() {
            @Override
            public ConsoleAnnotator annotate(@Nonnull Object context, @Nonnull MarkupText text) {
                text.addHyperlink(firstToken.length(), firstToken.length() + secondToken.length(), url);
                return this;
            }
        };
        ConsoleAnnotationOutputStream caos = new ConsoleAnnotationOutputStream(writer, annotator, null, StandardCharsets.UTF_8);
        caos.write((firstToken + secondToken + "\n").getBytes(StandardCharsets.UTF_8));
        caos.flush();
    }

    @Test
    public void testConsoleAnnotationOutputStream2() throws Exception {
        Writer writer = new PrintWriter(System.out);
        ConsoleAnnotator annotator = new UrlAnnotator().newInstance(null);
        ConsoleAnnotationOutputStream caos = new ConsoleAnnotationOutputStream(writer, annotator, null, StandardCharsets.UTF_8);
        String htmlHeader = "View logs in <a href=\"example.com\">Elastic</a>.\n";
        htmlHeader = "View logs in <a href=\"https://e1c29b6b6a564b1c9bbdfaffc6ea184c.europe-west1.gcp.cloud.es.io:9243/app/logs/stream?logPosition=(end:now,start:now-1d,streamLive:!f)&logFilter=(language:kuery,query:%27trace.id:8ab25bae32e68c81d4043522096b5ac7%27)&\" target=\"_blank\">Elastic Observability</a>";
        caos.write((/*ConsoleNote.PREAMBLE +*/ htmlHeader + "\n").getBytes(StandardCharsets.UTF_8));
        caos.flush();
    }
}
