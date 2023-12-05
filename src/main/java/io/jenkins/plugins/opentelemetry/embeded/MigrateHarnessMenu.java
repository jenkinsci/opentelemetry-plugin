package io.jenkins.plugins.opentelemetry.embeded;


import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.ModelObject;
import jenkins.model.TransientActionFactory;

import java.util.Collection;
import java.util.Collections;


@Extension
public class MigrateHarnessMenu extends TransientActionFactory<ModelObject> {
    @Override
    public Class<ModelObject> type() {
        return ModelObject.class;
    }
    @NonNull
    @Override
    public Collection<? extends Action> createFor(@NonNull ModelObject target) {
        MigrateHarnessUrlAction blueOceanUrlObject = new MigrateHarnessUrlAction();
        return Collections.singleton(blueOceanUrlObject);
    }

    @Override
    public Class<? extends Action> actionType() {
        return MigrateHarnessUrlAction.class;
    }
}
