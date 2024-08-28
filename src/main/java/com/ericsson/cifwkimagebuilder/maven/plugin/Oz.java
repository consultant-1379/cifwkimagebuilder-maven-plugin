package com.ericsson.cifwkimagebuilder.maven.plugin;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.List;
import java.util.Map;

public class Oz {
    private final Resources resources;
    private final Log log;

    public Oz(final Log log) {
        this.log = log;
        this.resources = new Resources();
    }

    public void create(final File buildDir, final File tdlFile, final File kickstart,
                       final String buildImageName, final String sparsifyImageName,
                       final String owner, final String group) throws MojoExecutionException {
        final String custScriptName = "oz_create.sh";
        final String scriptContents = this.resources.getResource(custScriptName);
        final File script = new File(buildDir, custScriptName);
        FileUtils.write(script, scriptContents);

        String ozLogLevel = "";
        if (this.log.isDebugEnabled()) {
            ozLogLevel = "-v4";
        }

        String command = "bash " + script.getAbsolutePath() +
                " -t " + tdlFile.getAbsolutePath() +
                " -d " + buildDir.getAbsolutePath() +
                " -n " + buildImageName +
                " -s " + sparsifyImageName +
                " -u " + owner + " -g " + group + " " + ozLogLevel;
        if (kickstart != null) {
            command += " -k " + kickstart.getAbsolutePath();
        }
        final ProcessHandler ph = new ProcessHandler(log);
        ph.setWorkingDirectory(buildDir);
        final int exitCode = ph.execute(command.trim());
        if (exitCode != 0) {
            throw new MojoExecutionException("Oz create session failed, exited " + exitCode);
        }
    }

    public void customize(final File buildDir,
                          final File tdlFile, final File kvmFile,
                          final String buildImageName, final String sparsifyImageName,
                          final String owner, final String group) throws MojoExecutionException {
        final String custScriptName = "oz_customize.sh";
        final String scriptContents = this.resources.getResource(custScriptName);
        final File script = new File(buildDir, custScriptName);
        FileUtils.write(script, scriptContents);

        String ozLogLevel = "";
        if (this.log.isDebugEnabled()) {
            ozLogLevel = "-v4";
        }

        final String command = "bash " + script.getAbsolutePath() +
                " -t " + tdlFile.getAbsolutePath() +
                " -k " + kvmFile.getAbsolutePath() +
                " -d " + buildDir.getAbsolutePath() +
                " -n " + buildImageName +
                " -s " + sparsifyImageName +
                " -u " + owner + " -g " + group + " " + ozLogLevel;

        final ProcessHandler ph = new ProcessHandler(log);
        ph.setWorkingDirectory(buildDir);
        final int exitCode = ph.execute(command.trim());
        if (exitCode != 0) {
            throw new MojoExecutionException("Oz customise session failed, exited " + exitCode);
        }
    }

    public String getTdl(final String name,
                         final String osName, final String osVersion, final String osArch,
                         final Map<String, String> yumRepos, final List<String> packages,
                         final String rootPassword, final String rootDiskSize,
                         final File jumpIso, final File cmdsConfig) throws MojoExecutionException {
        String template = this.getCommonTemplate(name, osName, osVersion, osArch, yumRepos, packages, rootPassword,
                rootDiskSize, cmdsConfig);
        String isoXml = " ";
        if (jumpIso != null) {
            isoXml = "file://" + jumpIso.getAbsolutePath();
        }
        return template.replaceAll("__OSURL__", isoXml);
    }

    public String getConfig(final String buildDir) throws MojoExecutionException {
        String template = this.resources.getResource("oz.cfg");
        return template.replaceAll("__BUILDDIR__", buildDir.trim());
    }

    private String getCommonTemplate(final String name,
                                     final String osName, final String osVersion, final String osArch,
                                     final Map<String, String> yumRepos, final List<String> packages,
                                     final String rootPassword, final String rootDiskSize,
                                     final File cmdsConfig) throws MojoExecutionException {
        String template = this.resources.getResource("oz.tdl");
        template = template.replaceAll("__ARTIFACT__", name.trim());
        template = template.replaceAll("__OSNAME__", osName.trim());
        template = template.replaceAll("__OSVERSION__", osVersion.trim());
        template = template.replaceAll("__OSARCH__", osArch.trim());

        final String repoTemplateXml = "<repository name='__NAME__'>\n" +
                "    <url>__URL__</url>\n" +
                "    <signed>no</signed>\n" +
                "    <persisted>no</persisted>\n" +
                "</repository>\n";
        String repoXml = "";
        for (Map.Entry<String, String> entry : yumRepos.entrySet()) {
            repoXml += repoTemplateXml.replace("__NAME__", entry.getKey().trim()).
                    replace("__URL__", entry.getValue().trim());
        }
        template = template.replace("__REPOS__", repoXml.trim());

        String pkgXml = "";
        for (String pkgName : packages) {
            pkgXml += "<package name='" + pkgName.trim() + "'/>\n";
        }
        template = template.replace("__PACKAGES__", pkgXml.trim());

        String cmdsCfgContents = "";
        if (cmdsConfig != null) {
            cmdsCfgContents = FileUtils.getContents(cmdsConfig);
        }
        template = template.replace("__COMMANDS__",
                "<command name='imageUpdates'>\n" + cmdsCfgContents.trim() + "\n</command>");

        String rootPassXml = "";
        if (rootPassword != null && rootPassword.trim().length() > 0) {
            rootPassXml = "<rootpw>" + rootPassword.trim() + "</rootpw>";
        }
        template = template.replaceAll("__ROOTPASS__", rootPassXml);

        String rootDiskXml = "";
        if (rootDiskSize != null && rootDiskSize.trim().length() > 0) {
            rootDiskXml = "<disk><size>" + rootDiskSize.trim() + "</size></disk>";
        }
        return template.replaceAll("__DISKSIZE__", rootDiskXml);
    }
}
