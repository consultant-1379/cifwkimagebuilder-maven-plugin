package com.ericsson.cifwkimagebuilder.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessHandler {
    private final Log log;
    private File workingDir = null;
    private String stdout = null;

    public ProcessHandler(final Log log) {
        this.log = log;
    }

    public void setWorkingDirectory(final File workingDir) {
        this.workingDir = workingDir;
    }

    public int execute(final String command) throws MojoExecutionException {
        return execute(command, true);
    }

    public int execute(final String command, final boolean logToStdout) throws MojoExecutionException {
        this.log.info("Executing: " + command);
        final ProcessBuilder pb = new ProcessBuilder(command.split(" "));
        if (this.workingDir != null) {
            pb.directory(this.workingDir);
        }
        pb.redirectErrorStream(true);
        try {
            final Process process = pb.start();
            final BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            this.stdout = "";
            while ((line = in.readLine()) != null) {
                if (logToStdout) {
                    this.log.info(line);
                }
                this.stdout += "\n" + line;
            }
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to execute command " + command, e);
        }
    }

    public String getStdout() {
        return this.stdout;
    }
}
