package com.ericsson.cifwkimagebuilder.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Resources {
    public String getResource(final String name) throws MojoExecutionException {
        final String resourcePath = "/templates/" + name;
        final InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new MojoExecutionException("Resource " + resourcePath + " not found!");
        }
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        final StringBuilder buffer = new StringBuilder();
        try {
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + resourcePath, e);
        }
        return buffer.toString().trim();
    }

}
