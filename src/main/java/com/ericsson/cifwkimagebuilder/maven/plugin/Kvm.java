package com.ericsson.cifwkimagebuilder.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.util.Random;

public class Kvm {
    public String getDomainXml(final String name, final String memory, final String vcpu,
                               final File image, final File seedIso) throws MojoExecutionException {
        final Resources resources = new Resources();
        String template = resources.getResource("domain.xml");

        template = template.replaceAll("__NAME__", name);
        template = template.replaceAll("__MEMORY__", memory);
        template = template.replaceAll("__VCPU__", vcpu);
        template = template.replaceAll("__MACADDRESS__", this.randomMACAddress());


        template = template.replaceAll("__IMAGE__", image.getAbsolutePath());
        template = template.replaceAll("__SEED__", seedIso.getAbsolutePath());

        return template.trim();
    }

    private String randomMACAddress() {
        Random rand = new Random();
        byte[] macAddr = new byte[3];
        rand.nextBytes(macAddr);

        macAddr[0] = (byte) (macAddr[0] & (byte) 254);  //zeroing last 2 bytes to make it unicast and locally adminstrated

        StringBuilder sb = new StringBuilder("52:54:00");
        for (byte b : macAddr) {

            if (sb.length() > 0)
                sb.append(":");

            sb.append(String.format("%02x", b));
        }


        return sb.toString();
    }
}
