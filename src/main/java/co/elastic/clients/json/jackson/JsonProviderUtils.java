/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package co.elastic.clients.json.jackson;

import jakarta.json.spi.JsonProvider;

/**
 * Workaround
 * {@code
 * java.lang.ClassNotFoundException: org.glassfish.json.JsonProviderImpl
 * 	at jenkins.util.AntClassLoader.findClassInComponents(AntClassLoader.java:1387)
 * 	at jenkins.util.AntClassLoader.findClass(AntClassLoader.java:1342)
 * 	at jenkins.util.AntClassLoader.loadClass(AntClassLoader.java:1089)
 * 	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522)
 * 	at java.base/java.lang.Class.forName0(Native Method)
 * 	at java.base/java.lang.Class.forName(Class.java:315)
 * 	at jakarta.json.spi.JsonProvider.provider(JsonProvider.java:72)
 * Caused: jakarta.json.JsonException: Provider org.glassfish.json.JsonProviderImpl not found
 * 	at jakarta.json.spi.JsonProvider.provider(JsonProvider.java:75)
 * 	at co.elastic.clients.json.jackson.JsonValueParser.<init>(JsonValueParser.java:39)
 * 	at co.elastic.clients.json.jackson.JacksonJsonpParser.getValue(JacksonJsonpParser.java:233)
 * 	at co.elastic.clients.json.JsonData.from(JsonData.java:96)
 * 	at co.elastic.clients.json.JsonpDeserializer$2.deserialize(JsonpDeserializer.java:112)
 * 	at co.elastic.clients.elasticsearch._types.ErrorCause.lambda$setupErrorCauseDeserializer$0(ErrorCause.java:391)
 * 	at co.elastic.clients.json.ObjectDeserializer.parseUnknownField(ObjectDeserializer.java:205)
 * 	at co.elastic.clients.json.ObjectDeserializer.deserialize(ObjectDeserializer.java:174)
 * 	at co.elastic.clients.json.ObjectDeserializer.deserialize(ObjectDeserializer.java:137)
 * 	at co.elastic.clients.json.ObjectBuilderDeserializer.deserialize(ObjectBuilderDeserializer.java:85)
 * 	at co.elastic.clients.json.DelegatingDeserializer$SameType.deserialize(DelegatingDeserializer.java:48)
 * 	at co.elastic.clients.json.JsonpDeserializerBase$ArrayDeserializer.deserialize(JsonpDeserializerBase.java:320)
 * 	at co.elastic.clients.json.JsonpDeserializerBase$ArrayDeserializer.deserialize(JsonpDeserializerBase.java:285)
 * 	at co.elastic.clients.json.JsonpDeserializer.deserialize(JsonpDeserializer.java:75)
 * 	at co.elastic.clients.json.ObjectDeserializer$FieldObjectDeserializer.deserialize(ObjectDeserializer.java:72)
 * 	at co.elastic.clients.json.ObjectDeserializer.deserialize(ObjectDeserializer.java:176)
 * 	at co.elastic.clients.json.ObjectDeserializer.deserialize(ObjectDeserializer.java:137)
 * 	at co.elastic.clients.json.JsonpDeserializer.deserialize(JsonpDeserializer.java:75)
 * 	at co.elastic.clients.json.ObjectBuilderDeserializer.deserialize(ObjectBuilderDeserializer.java:79)
 * 	at co.elastic.clients.json.DelegatingDeserializer$SameType.deserialize(DelegatingDeserializer.java:43)
 * 	at co.elastic.clients.json.ObjectDeserializer$FieldObjectDeserializer.deserialize(ObjectDeserializer.java:72)
 * 	at co.elastic.clients.json.ObjectDeserializer.deserialize(ObjectDeserializer.java:176)
 * 	at co.elastic.clients.json.ObjectDeserializer.deserialize(ObjectDeserializer.java:137)
 * 	at co.elastic.clients.json.JsonpDeserializer.deserialize(JsonpDeserializer.java:75)
 * 	at co.elastic.clients.json.ObjectBuilderDeserializer.deserialize(ObjectBuilderDeserializer.java:79)
 * 	at co.elastic.clients.json.DelegatingDeserializer$SameType.deserialize(DelegatingDeserializer.java:43)
 * 	at co.elastic.clients.transport.rest_client.RestClientTransport.getHighLevelResponse(RestClientTransport.java:276)
 * 	at co.elastic.clients.transport.rest_client.RestClientTransport.performRequest(RestClientTransport.java:144)
 * 	at co.elastic.clients.elasticsearch.ElasticsearchClient.search(ElasticsearchClient.java:1487)
 * 	at io.jenkins.plugins.opentelemetry.backend.elastic.ElasticsearchLogStorageScrollingRetriever.overallLog(ElasticsearchLogStorageScrollingRetriever.java:113)
 * 	at io.jenkins.plugins.opentelemetry.job.log.OtelLogStorage.overallLog(OtelLogStorage.java:98)
 * 	at org.jenkinsci.plugins.workflow.job.WorkflowRun.getLogText(WorkflowRun.java:1057)
 * 	at hudson.model.Run.writeLogTo(Run.java:1554)
 * 	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
 * 	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
 * 	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
 * 	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
 * }
 */
public class JsonProviderUtils {
    public static JsonProvider provider(ClassLoader classLoader) {
        return new org.eclipse.parsson.JsonProviderImpl();
    }
}
