/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.rules;

import jenkins.plugins.git.CliGitCommand;
import jenkins.scm.impl.mock.AbstractSampleDVCSRepoRule;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Manages a sample Git repository.
 * Extension of {@link AbstractSampleDVCSRepoRule}
 */
public final class ExtendedGitSampleRepoRule extends AbstractSampleDVCSRepoRule implements BeforeEachCallback, AfterEachCallback {

    private static boolean initialized = false;

    private static final String DEFAULT_INITIAL_BRANCH = "master";

    private final String initialBranch;

    /**
     * Creates a Rule with a Git repository that may be initialized with a "master" branch and cloned from a local repository
     */
    public ExtendedGitSampleRepoRule() {
        this(DEFAULT_INITIAL_BRANCH);
    }

    /**
     * Creates a Rule with a Git repository that may be initialized with the provided initial branch named and cloned from a local repository
     * @param initialBranch initial branch name. Commonly "master" or "main".
     */
    public ExtendedGitSampleRepoRule(String initialBranch) {
        this.initialBranch = initialBranch;
    }

    public void git(String... cmds) throws Exception {
        run("git", cmds);
    }

    private static void checkGlobalConfig() throws Exception {
        if (initialized) return;
        initialized = true;
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();
    }

    @Override
    public void init() throws Exception {
        run(true, tmp.getRoot(), "git", "version");
        checkGlobalConfig();
        git("init");
        git("checkout", "-b", this.initialBranch);
        write("file", "");
        git("add", "file");
        git("config", "user.name", "Git SampleRepoRule");
        git("config", "user.email", "gits@mplereporule");
        git("commit", "--message=init");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        try {
            this.before();
        } catch (Throwable t) {
            throw new ExtensionConfigurationException(t.getMessage(), t);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        this.after();
    }

}
