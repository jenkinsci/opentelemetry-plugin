package io.jenkins.plugins.opentelemetry.context;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;
import io.opentelemetry.context.Scope;

import javax.annotation.Nullable;
import java.util.logging.Logger;

public class JenkinsRunContextStorageProvider implements ContextStorageProvider {

    private static Logger LOGGRER = Logger.getLogger(JenkinsRunContextStorageProvider.class.getName());
    /**
     * @see ContextStorage
     */
    @Override
    public ContextStorage get() {
        ContextStorage threadLocalStorage = ContextStorage.defaultStorage();

        return new ContextStorage() {
            @Override
            public Scope attach(Context toAttach) {
                Context current = current();
                setMdc(toAttach);
                Scope scope = threadLocalStorage.attach(toAttach);
                return new Scope() {
                    @Override
                    public void close() {
                        clearMdc();
                        setMdc(current);
                        scope.close();
                    }
                };
            }

            private void setMdc(Context toAttach) {
            }

            private void clearMdc() {
            }

            @Nullable
            @Override
            public Context current() {
                return threadLocalStorage.current();
            }
        };
    }
}
