/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package co.elastic.clients.json.jackson;

import jakarta.json.JsonException;
import jakarta.json.spi.JsonProvider;

import java.util.Iterator;
import java.util.ServiceLoader;

public class JsonProviderUtils {
    public static JsonProvider provider(ClassLoader classLoader) {

        return new org.eclipse.parsson.JsonProviderImpl();
        // ServiceLoader<JsonProvider> loader = ServiceLoader.load(JsonProvider.class, classLoader);
        // Iterator<JsonProvider> it = loader.iterator();
        // if (it.hasNext()) {
        //     return (JsonProvider)it.next();
        // } else {
        //     try {
        //         Class<?> clazz = Class.forName("org.glassfish.json.JsonProviderImpl");
        //         return (JsonProvider)clazz.newInstance();
        //     } catch (ClassNotFoundException var3) {
        //         throw new JsonException("Provider org.glassfish.json.JsonProviderImpl not found", var3);
        //     } catch (Exception var4) {
        //         throw new JsonException("Provider org.glassfish.json.JsonProviderImpl could not be instantiated: " + var4, var4);
        //     }
        // }
    }
}
