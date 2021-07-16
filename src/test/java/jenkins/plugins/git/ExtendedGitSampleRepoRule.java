/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jenkins.plugins.git;

import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.scm.impl.mock.AbstractSampleDVCSRepoRule;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Manages a sample Git repository.
 * Extension of {@link GitSampleRepoRule}
 */
public final class ExtendedGitSampleRepoRule extends AbstractSampleDVCSRepoRule {

    private static boolean initialized = false;

    private static final Logger LOGGER = Logger.getLogger(jenkins.plugins.git.GitSampleRepoRule.class.getName());

    private final static String DEFAULT_INITIAL_BRANCH = "master";

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
        git("init", "--initial-branch=" + this.initialBranch);
        write("file", "");
        git("add", "file");
        git("config", "user.name", "Git SampleRepoRule");
        git("config", "user.email", "gits@mplereporule");
        git("commit", "--message=init");
    }

    public final boolean mkdirs(String rel) throws IOException {
        return new File(this.sampleRepo, rel).mkdirs();
    }

    public void notifyCommit(JenkinsRule r) throws Exception {
        synchronousPolling(r);
        WebResponse webResponse = r.createWebClient().goTo("git/notifyCommit?url=" + bareUrl(), "text/plain").getWebResponse();
        LOGGER.log(Level.FINE, webResponse.getContentAsString());
        for (NameValuePair pair : webResponse.getResponseHeaders()) {
            if (pair.getName().equals("Triggered")) {
                LOGGER.log(Level.FINE, "Triggered: " + pair.getValue());
            }
        }
        r.waitUntilNoActivity();
    }

    public String head() throws Exception {
        return new RepositoryBuilder().setWorkTree(sampleRepo).build().resolve(Constants.HEAD).name();
    }

    public File getRoot() {
        return this.sampleRepo;
    }

    public boolean gitVersionAtLeast(int neededMajor, int neededMinor) {
        return gitVersionAtLeast(neededMajor, neededMinor, 0);
    }

    public boolean gitVersionAtLeast(int neededMajor, int neededMinor, int neededPatch) {
        final TaskListener procListener = StreamTaskListener.fromStderr();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            int returnCode = new Launcher.LocalLauncher(procListener).launch().cmds("git", "--version").stdout(out).join();
            if (returnCode != 0) {
                LOGGER.log(Level.WARNING, "Command 'git --version' returned " + returnCode);
            }
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.WARNING, "Exception checking git version " + ex);
        }
        final String versionOutput = out.toString().trim();
        final String[] fields = versionOutput.split(" ")[2].replaceAll("msysgit.", "").replaceAll("windows.", "").split("\\.");
        final int gitMajor = Integer.parseInt(fields[0]);
        final int gitMinor = Integer.parseInt(fields[1]);
        final int gitPatch = Integer.parseInt(fields[2]);
        if (gitMajor < 1 || gitMajor > 3) {
            LOGGER.log(Level.WARNING, "Unexpected git major version " + gitMajor + " parsed from '" + versionOutput + "', field:'" + fields[0] + "'");
        }
        if (gitMinor < 0 || gitMinor > 50) {
            LOGGER.log(Level.WARNING, "Unexpected git minor version " + gitMinor + " parsed from '" + versionOutput + "', field:'" + fields[1] + "'");
        }
        if (gitPatch < 0 || gitPatch > 20) {
            LOGGER.log(Level.WARNING, "Unexpected git patch version " + gitPatch + " parsed from '" + versionOutput + "', field:'" + fields[2] + "'");
        }

        return gitMajor > neededMajor ||
            (gitMajor == neededMajor && gitMinor > neededMinor) ||
            (gitMajor == neededMajor && gitMinor == neededMinor && gitPatch >= neededPatch);
    }
}

