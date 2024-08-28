package com.ericsson.cifwkimagebuilder.maven.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

enum Psource {
    SYSTEM, MAVEN
}

enum Property {
    BUILD_DIR("build.dir", "/dev/shm"),
    BUILD_TYPE("build.type", false),
    SEED_ISO("seed.iso", false),
    JUMP_ISO("jump.iso", false),
    SET_DISK("set.disk", false),
    SET_ROOTPASS("set.rootpass", true),
    PACKAGING_TYPE("packaging.type", "qcow2"),
    COMMANDS_CONFIG("commands.config.file", "commands.config"),
    OS_ARCH("osArch", false),
    OS_NAME("osName", false),
    OS_VERSION("osVersion", false),
    ARTIFACT_TO_INSTALL("artifact.to.install", true),
    KGB_PACKAGE_LIST("kgb.package.list", true),
    KVM_MEMORY("kvm.memory", "2097152"),
    KVM_VCPU("kvm.vcpu", "4"),
    USER_NAME("user.name", false),
    KICKSTART("kickstart", true),
    REPO_CREATE_URL("getImageCreationDropRepo", "https://ci-portal.seli.wh.rnd.internal.ericsson.com/createRepo/"),
    REPO_DELETE_URL("deleteImageCreationDropRepo", "https://ci-portal.seli.wh.rnd.internal.ericsson.com/deleteRepo/");

    private final String name;
    private boolean nullable = false;
    private String defaultValue = null;

    Property(final String name, final boolean nullable) {
        this.name = name;
        this.nullable = nullable;
    }

    Property(final String name, final String defaultValue) {
        this(name, true);
        this.defaultValue = defaultValue;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public String defaultValue() {
        return this.defaultValue;
    }

    public boolean isNullable() {
        return this.nullable;
    }

}

public class PluginProperties {
    private static final Map<Psource, Properties> s_PROPS = new HashMap<>();


    public static void init(final Psource source, final Properties props) {
        if (s_PROPS.containsKey(source)) {
            s_PROPS.get(source).putAll(props);
        } else {
            s_PROPS.put(source, props);
        }
    }

    public static List<String> getKeys(final String keyRegex) {
        final List<String> matchingKeys = new ArrayList<>();
        for (Psource ps : Psource.values()) {
            for (Object keyName : s_PROPS.get(ps).keySet()) {
                if (keyName.toString().matches(keyRegex)) {
                    matchingKeys.add(keyName.toString());
                }
            }
        }
        return matchingKeys;
    }

    public static String getProperty(final Property property) {
        return getProperty(property, null);
    }

    public static String getProperty(final Property property, final Psource source) {
        String value = null;
        if (source != null && s_PROPS.containsKey(source)) {
            value = s_PROPS.get(source).getProperty(property.toString());
        } else {
            for (Psource ps : Psource.values()) {
                value = s_PROPS.get(ps).getProperty(property.toString());
                if (value != null) {
                    break;
                }
            }
        }
        if (value == null) {
            if (property.isNullable()) {
                return property.defaultValue();
            } else {
                throw new RuntimeException("Property " + property + " has not value set!");
            }
        }
        return value;
    }

    public static String getProperty(final String propertyName) {
        for (Psource ps : Psource.values()) {
            final String value = getProperty(propertyName, ps);
            if (value != null) {
                return value;
            }
        }
        return null;
    }


    public static String getProperty(final String propertyName, final Psource source) {
        if (s_PROPS.containsKey(source)) {
            return s_PROPS.get(source).getProperty(propertyName);
        }
        return null;
    }
//
//    public static String getProperty(final Property property) {
//        for (Psource ps : Psource.values()) {
//            final String value = getProperty(property, ps);
//            if (value != null) {
//                return value;
//            }
//        }
//        return property.defaultValue();
//    }
//
}
