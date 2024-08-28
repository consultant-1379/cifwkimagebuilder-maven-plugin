package com.ericsson.cifwkimagebuilder.maven.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class MavenRepository {
    private final RepositorySystemSession repositorySystemSession;
    private final MavenProject project;
    private Log log;

    public MavenRepository(final MavenProject project, final RepositorySystemSession systemSession, final Log log) {
        this.repositorySystemSession = systemSession;
        this.project = project;
        this.log = log;
    }

    private void info(final String message) {
        this.log.info(message);
    }

    public File getArtifact(final Artifact artifact, final File targetDir) throws MojoExecutionException {
        return this.getArtifact(artifact, artifact.getType(), targetDir);
    }

    public File getArtifact(final Artifact artifact, final String type, final File targetDir) throws MojoExecutionException {
        final File localRepoFile = getLocalPath(artifact, type);
        this.info("Possible local artifact " + localRepoFile.getAbsolutePath());

        // Check the local .m2 repo file, if not there then download it.
        if (!localRepoFile.exists()) {
            this.info("Artifact " + artifact.toString() + " not found locally, downloading to " + targetDir.getAbsolutePath());
            this.getRemoteArtifact(artifact, type, localRepoFile);
        } else {
            this.info("Artifact " + localRepoFile.getName() + " found in local repo.");
        }
        final File targetFile;
        if (targetDir != null) {
            targetFile = new File(targetDir, localRepoFile.getName());
            FileUtils.copyFile(localRepoFile, targetFile);
        } else {
            targetFile = localRepoFile;
        }
        return targetFile;
    }

    private void getRemoteArtifact(final Artifact artifact, final String type, final File targetFile) throws MojoExecutionException {
        final List<ArtifactRepository> repos = new ArrayList<>(this.project.getRemoteArtifactRepositories());
        boolean remoteFound = false;
        for (ArtifactRepository repo : repos) {
            final String artifactURL = this.getRemotePath(artifact, type, repo);
            try {
                this.info("Trying remote " + artifactURL);
                this.getRemoteFile(artifactURL, targetFile);
                remoteFound = true;
                break;
            } catch (FileNotFoundException e) {
                this.info(artifactURL + " not found!");
            }
        }
        if (!remoteFound) {
            throw new MojoExecutionException("Could not find " + artifact.toString() + " in any remote maven repository");
        }
    }

    private void getRemoteFile(final String artifactURL, final File dest) throws FileNotFoundException, MojoExecutionException {
        final File destFilepart = new File(dest.getAbsolutePath() + ".filepart");
        this.download(artifactURL, destFilepart, true);

        final String remote_md5File = artifactURL + ".md5";
        final File local_md5File = new File(dest.getAbsolutePath() + ".md5");
        this.download(remote_md5File, local_md5File, false);

        this.info("Checking filepart md5 ...");
        if (!this.checkMd5(destFilepart, local_md5File)) {
            throw new MojoExecutionException("MD5 check on " + destFilepart.getAbsolutePath() + " failed!!!");
        }
        FileUtils.renameFile(destFilepart, dest);
        this.info("Renamed " + destFilepart.getName() + " to " + dest.getName());
    }

    private boolean checkMd5(final File testFile, final File md5File) throws MojoExecutionException {
        final String md5Sum = FileUtils.getContents(md5File).trim();
        final File md5opts = new File(md5File.getParentFile(), "md5opts");
        md5opts.deleteOnExit();
        // the 2 space bit is important
        FileUtils.write(md5opts, md5Sum + "  " + testFile.getName());
        final ProcessHandler process = new ProcessHandler(this.log);
        process.setWorkingDirectory(testFile.getParentFile());
        final int exitCode = process.execute("/usr/bin/md5sum --check " + md5opts.getPath());
        return exitCode == 0;
    }

    private void download(final String artifactURL, final File dest, boolean continuePrevious) throws FileNotFoundException, MojoExecutionException {
        try {
            this.info("Downloading " + artifactURL + " to " + dest.getAbsolutePath());
            final URL url = new URL(artifactURL);
            final URLConnection urlConn = url.openConnection();

            boolean appendToDest = false;
            if (dest.exists() && continuePrevious) {
                this.info("Continuing previous download ...");
                urlConn.setRequestProperty("Range", "bytes=" + (dest.length()) + "-");
                appendToDest = true;
            }
            final BufferedInputStream is = new BufferedInputStream(urlConn.getInputStream());
            final BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(dest, appendToDest));
            byte[] b = new byte[8 * 1024];
            int read;
            while ((read = is.read(b)) > -1) {
                bout.write(b, 0, read);
            }
            bout.flush();
            bout.close();
            is.close();
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to download " + artifactURL, e);
        }
    }

    private File getLocalPath(final Artifact artifact, final String type) {
        final LocalRepository localRepo = this.repositorySystemSession.getLocalRepository();
        return new File(localRepo.getBasedir(), this.getRepoPath(artifact, type));
    }

    private String getRemotePath(final Artifact artifact, final String type, final ArtifactRepository repo) {
        return repo.getUrl() + "/" + this.getRepoPath(artifact, type);
    }

    public String toFileName(final Artifact artifact) {
        return toFileName(artifact, null);
    }

    public String toFileName(final Artifact artifact, final String type) {
        String name = artifact.getArtifactId() + "-" + artifact.getVersion();
        if (artifact.getClassifier() != null) {
            name += "-" + artifact.getClassifier();
        }
        if (type != null) {
            name += "." + type;
        }
        return name;
    }

    private String getRepoPath(final Artifact artifact, final String type) {
        final File groupDir = new File(artifact.getGroupId().replaceAll("\\.", "/"));
        return new File(groupDir, artifact.getArtifactId() + "/" + artifact.getVersion() + "/" + toFileName(artifact, type)).getPath();
    }
}
