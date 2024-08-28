package com.ericsson.cifwkimagebuilder.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;

public class FileUtils {
    public static String getContents(final File file) throws MojoExecutionException {
        try {
            return org.apache.commons.io.FileUtils.readFileToString(file);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + file.getAbsolutePath(), e);
        }
    }

    public static void copyFile(final File src, final File dest) throws MojoExecutionException {
        try {
            org.apache.commons.io.FileUtils.copyFile(src, dest);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy " + src.getAbsolutePath() +
                    " to " + dest.getAbsolutePath(), e);
        }
    }

    public static void setOwner(final File file, final String principal) throws MojoExecutionException {
        final Path path = Paths.get(file.getAbsolutePath());
        final UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
        try {
            UserPrincipal userPrincipal = lookupService.lookupPrincipalByName(principal);
            Files.setOwner(path, userPrincipal);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to set owner of " + file.getAbsolutePath() + " to " + principal, e);
        }
    }

    public static void write(final File targetFile, final String contents) throws MojoExecutionException {
        try {
            org.apache.commons.io.FileUtils.write(targetFile, contents, false);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write to " + targetFile.getAbsolutePath(), e);
        }
    }

    public static void renameFile(final File src, final File newName) throws MojoExecutionException {
        if (!src.renameTo(newName)) {
            throw new MojoExecutionException("Failed to rename " + src.getAbsolutePath());
        }
    }
}
