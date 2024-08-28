package com.ericsson.cifwkimagebuilder.maven.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Scanner;

/**
 * @goal buildImage
 * @phase compile
 * @requiresProject false
 */
public class BuildImage extends AbstractMojo {

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    @Parameter
    private MavenProject mavenProject;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    private static final String BUILD_TYPE_CUSTOMIZE = "customize";
    private static final String BUILD_TYPE_CREATE = "create";
    private static final String GROUP_QEMU = "qemu";

    private MavenRepository mavenRepository = null;
    private YumRepository productRepoBuilder = null;
    private String buildUser = null;
    private String buildGroup = null;
    private File buildDir = null;

    public void execute() throws MojoExecutionException {
        PluginProperties.init(Psource.MAVEN, this.mavenProject.getProperties());
        PluginProperties.init(Psource.SYSTEM, System.getProperties());

        this.mavenRepository = new MavenRepository(this.mavenProject, this.repoSession, getLog());
        this.productRepoBuilder = new YumRepository(getLog());
        this.buildUser = PluginProperties.getProperty(Property.USER_NAME);
        this.buildGroup = this.getPrimaryGroup(buildUser);
        this.buildDir = new File(PluginProperties.getProperty(Property.BUILD_DIR));

        this.setupChecks();

        final String buildType = PluginProperties.getProperty(Property.BUILD_TYPE);
        if (BUILD_TYPE_CUSTOMIZE.equals(buildType)) {
            this.doCustomizeBuild();
        } else if (BUILD_TYPE_CREATE.equals(buildType)) {
            this.doCreateBuild();
        } else {
            throw new MojoExecutionException("Not implemented!");
        }
    }

    private void doCreateBuild() throws MojoExecutionException {
        final Map<String, String> productDropRepos = this.getProductRepos();
        final Oz oz = new Oz(getLog());
        final File ozFile = this.getOzTemplate(oz, productDropRepos);
        getLog().info("OZ TDL: " + ozFile.getAbsolutePath());

        final File ozConfig = this.getOzConfig(oz);
        getLog().info("OZ Config: " + ozConfig.getAbsolutePath());

        final String ksName = PluginProperties.getProperty(Property.KICKSTART);
        File kickstartFile = null;
        if (ksName != null) {
            final File repoFile = new File(ksName);
            kickstartFile = new File(this.buildDir, repoFile.getName());
            FileUtils.copyFile(repoFile, kickstartFile);
            getLog().info("Kickstart is " + kickstartFile.getAbsolutePath());
        }

        final String imageFilename = this.mavenRepository.toFileName(this.mavenProject.getArtifact(),
                PluginProperties.getProperty(Property.PACKAGING_TYPE));

        oz.create(buildDir, ozFile, kickstartFile, imageFilename, "compressed_" + imageFilename,
                this.buildUser, this.buildGroup);
    }

    private void doCustomizeBuild() throws MojoExecutionException {
        final Map<String, String> productDropRepos = this.getProductRepos();
        final Oz oz = new Oz(getLog());

        try {

            final File seedIso = this.getSeedIso();
            getLog().info("Seed ISO: " + seedIso.getAbsolutePath());

            final File kvmImage = this.getKvmBuildImage();
            getLog().info("Base Image: " + kvmImage.getAbsolutePath());

            final File ozConfig = this.getOzConfig(oz);
            getLog().info("OZ Config: " + ozConfig.getAbsolutePath());

            final File ozFile = this.getOzTemplate(oz, productDropRepos);
            getLog().info("OZ TDL: " + ozFile.getAbsolutePath());
            try {
                final String ozFileContents = new Scanner(ozFile).useDelimiter("\\Z").next();
                getLog().info("OZ TDL contents:\n" + ozFileContents);
            } catch (FileNotFoundException fileNotFoundExp) {
                getLog().info("Unable to display contents!");
            }

            final File kvmXml = this.getKvmDomain(kvmImage, seedIso);
            getLog().info("KVM XML: " + kvmXml.getAbsolutePath());

            oz.customize(this.buildDir, ozFile, kvmXml, kvmImage.getName(), "compressed_" + kvmImage.getName(),
                    this.buildUser, this.buildGroup);
        } finally {
            this.deleteProductRepos(productDropRepos);
        }
    }

    private Map<String, String> getProductRepos() throws MojoExecutionException {

        final String keyPrefix = "product_repo";
        final Map<String, String> repos = new HashMap<>();
        for (String key : PluginProperties.getKeys("^" + keyPrefix + "\\..*")) {
            final String productName = key.replace(keyPrefix + ".", "");
            final String productVersion = PluginProperties.getProperty(key);
            final String repoId = productName + ":" + productVersion;

            final String kgbPackages = PluginProperties.getProperty(Property.KGB_PACKAGE_LIST + "." + productName);
            getLog().info("Creating drop yum repo for product " + repoId);
            if (kgbPackages != null) {
                getLog().info("Including KGB packages in " + repoId + " -> " + kgbPackages);
            }
            final String dropRepo = this.productRepoBuilder.buildProductRepo(productName, productVersion, kgbPackages);
            getLog().info("Drop yum repo for " + repoId + " is " + dropRepo);
            repos.put(repoId + ":" + new File(dropRepo).getName(), dropRepo);
        }
        return repos;
    }

    private void deleteProductRepos(final Map<String, String> repos) throws MojoExecutionException {
        for (String repoUrl : repos.values()) {
            getLog().info("Removing drop yum repo " + repoUrl);
            this.productRepoBuilder.deleteProductRepo(repoUrl);
        }
    }

    private File getSeedIso() throws MojoExecutionException {
        final String seedIso = PluginProperties.getProperty(Property.SEED_ISO);
        if (seedIso == null) {
            throw new MojoExecutionException("No seed ISO set (" + Property.SEED_ISO + ")");
        }
        final Artifact seedArtifact = this.findArtifact(seedIso);
        if (seedArtifact == null) {
            throw new MojoExecutionException("No artifact for " + seedIso + " found!");
        }
        final File seedFile = this.mavenRepository.getArtifact(seedArtifact, this.buildDir);
        seedFile.setReadable(true, false);
        seedFile.setWritable(true, false);
        return seedFile;
    }

    private Artifact findArtifact(final String artifactId) {
        final Set<Artifact> dependencies = new HashSet<>(this.mavenProject.getDependencyArtifacts());
        for (Artifact artifact : dependencies) {
            if (artifact.getArtifactId().equals(artifactId)) {
                return artifact;
            }
        }
        return null;
    }

    private File getKvmBuildImage() throws MojoExecutionException {
        final MavenProject parent = this.mavenProject.getParent();
        final File parentImage = this.mavenRepository.getArtifact(parent.getArtifact(),
                PluginProperties.getProperty(Property.PACKAGING_TYPE), this.buildDir);
        final File workingImage = new File(this.buildDir,
                this.mavenRepository.toFileName(this.mavenProject.getArtifact(),
                        PluginProperties.getProperty(Property.PACKAGING_TYPE)));
        if (workingImage.exists()) {
            if (!workingImage.delete()) {
                getLog().warn("Couldn't delete " + workingImage.getAbsolutePath());
            }
        }
        FileUtils.renameFile(parentImage, workingImage);
        workingImage.setReadable(true, false);
        workingImage.setWritable(true, false);
        return workingImage;
    }

    private File getKvmDomain(final File kvmImage, final File seedIso) throws MojoExecutionException {
        final Kvm kvm = new Kvm();
        final String domainXml = kvm.getDomainXml(this.mavenProject.getArtifactId(),
                PluginProperties.getProperty(Property.KVM_MEMORY),
                PluginProperties.getProperty(Property.KVM_VCPU),
                kvmImage, seedIso);
        getLog().debug(domainXml);
        final File kvmFile = new File(this.buildDir, "kvm_domain.xml");
        FileUtils.write(kvmFile, domainXml);
        return kvmFile;
    }

    private File getOzConfig(final Oz oz) throws MojoExecutionException {
        final String configContents = oz.getConfig(this.buildDir.getAbsolutePath());

        getLog().debug(configContents);
        final File configFile = new File(this.buildDir, "oz.cfg");
        FileUtils.write(configFile, configContents);
        return configFile;
    }

    private File getOzTemplate(final Oz oz, final Map<String, String> productRepos) throws MojoExecutionException {
        final String buildType = PluginProperties.getProperty(Property.BUILD_TYPE);

        final Map<String, String> yumRepos = new HashMap<>();
        final String repoKeyPrefix = "yum_repo";
        for (String repoId : PluginProperties.getKeys("^" + repoKeyPrefix + "\\..*")) {
            final String name = repoId.replace(repoKeyPrefix + ".", "");
            yumRepos.put(name, PluginProperties.getProperty(repoId));
        }
        yumRepos.putAll(productRepos);

        final List<String> packages = new ArrayList<>();
        final String pkgNames = PluginProperties.getProperty(Property.ARTIFACT_TO_INSTALL, Psource.MAVEN);
        if (pkgNames != null) {
            packages.addAll(Arrays.asList(pkgNames.split(",")));
        }

        final String extraArtifacts = PluginProperties.getProperty(Property.ARTIFACT_TO_INSTALL, Psource.SYSTEM);
        if(extraArtifacts != null && extraArtifacts.length() > 0){
            packages.addAll(Arrays.asList(extraArtifacts.split(",")));
        }

        final String cmdsCfgFilename = PluginProperties.getProperty(Property.COMMANDS_CONFIG);
        File cmdsConfig = null;
        if (cmdsCfgFilename != null) {
            cmdsConfig = new File(this.mavenProject.getBasedir(), cmdsCfgFilename);
        }

        File jumpIso = null;
        if (BUILD_TYPE_CREATE.equals(buildType)) {
            final String jumpIsoId = PluginProperties.getProperty(Property.JUMP_ISO);
            if (jumpIsoId == null) {
                throw new MojoExecutionException("No Jump ISO set (" + Property.JUMP_ISO + ")");
            }
            final Artifact jumpArtifact = this.findArtifact(jumpIsoId);
            if (jumpArtifact == null) {
                throw new MojoExecutionException("No artifact for " + jumpIsoId + " found!");
            }
            jumpIso = this.mavenRepository.getArtifact(jumpArtifact, null);
        }

        final String name = this.mavenRepository.toFileName(this.mavenProject.getArtifact());
        final String tdlContents = oz.getTdl(name,
                PluginProperties.getProperty(Property.OS_NAME),
                PluginProperties.getProperty(Property.OS_VERSION),
                PluginProperties.getProperty(Property.OS_ARCH),
                yumRepos, packages, PluginProperties.getProperty(Property.SET_ROOTPASS),
                PluginProperties.getProperty(Property.SET_DISK), jumpIso, cmdsConfig);

        getLog().debug(tdlContents);
        final File tdlFile = new File(this.buildDir, "oz_" + buildType + ".tdl");
        FileUtils.write(tdlFile, tdlContents);
        return tdlFile;
    }


    private void setupChecks() throws MojoExecutionException {
        ProcessHandler ph = new ProcessHandler(getLog());
        ph.execute("/usr/bin/groups", false);
        boolean inQemuGroup = false;
        for (String group : ph.getStdout().split(" ")) {
            if (GROUP_QEMU.equals(group)) {
                inQemuGroup = true;
                break;
            }
        }
        if (!inQemuGroup) {
            getLog().warn("User " + PluginProperties.getProperty(Property.USER_NAME) + " not in the group " + GROUP_QEMU);
        }
    }

    private String getPrimaryGroup(final String user) throws MojoExecutionException {
        final ProcessHandler ph = new ProcessHandler(getLog());
        ph.execute("/usr/bin/id -g -n " + user, false);
        return ph.getStdout().trim();
    }
}
