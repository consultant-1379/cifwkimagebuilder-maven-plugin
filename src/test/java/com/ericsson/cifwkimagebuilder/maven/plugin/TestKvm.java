package com.ericsson.cifwkimagebuilder.maven.plugin;

import junit.framework.TestCase;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class TestKvm extends TestCase {
    private Kvm testClass = null;

    public TestKvm() {
        super();
    }

    @Override
    @Before
    protected void setUp() throws Exception {
        this.testClass = new Kvm();
    }

    @Test
    public void testGetDomainXml() throws MojoExecutionException {
        final File img = new File("img");
        final File seed = new File("seed");
        this.testClass.getDomainXml("test", "1", "2", img, seed);
    }
}
