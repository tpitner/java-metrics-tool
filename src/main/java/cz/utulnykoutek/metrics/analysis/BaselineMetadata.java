// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.analysis;

import cz.utulnykoutek.metrics.config.ConfigurationProvenance;
import cz.utulnykoutek.metrics.parser.JdtParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata captured for T0 baseline reproducibility.
 */
public final class BaselineMetadata {
    public final String toolVersion;
    public final String jdtCoreVersion;
    public final String jdkVersion;
    public final String jdkVendor;
    public final Map<String, Object> parser;
    public final Map<String, Object> configuration;

    public BaselineMetadata(String toolVersion, String jdtCoreVersion, String jdkVersion,
                            String jdkVendor, Map<String, Object> parser) {
        this(toolVersion, jdtCoreVersion, jdkVersion, jdkVendor, parser, Map.of());
    }

    public BaselineMetadata(String toolVersion, String jdtCoreVersion, String jdkVersion,
                            String jdkVendor, Map<String, Object> parser,
                            Map<String, Object> configuration) {
        this.toolVersion = toolVersion;
        this.jdtCoreVersion = jdtCoreVersion;
        this.jdkVersion = jdkVersion;
        this.jdkVendor = jdkVendor;
        // The parser snapshot intentionally retains explicit null values (for
        // example an unspecified Java release), so Map.copyOf is not suitable.
        this.parser = Collections.unmodifiableMap(new LinkedHashMap<>(parser));
        this.configuration = Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
    }

    public static BaselineMetadata capture(JdtParser.Config config) {
        return capture(config, null);
    }

    public static BaselineMetadata capture(JdtParser.Config config, ConfigurationProvenance provenance) {
        String toolVersion = detectToolVersion();
        String jdt = detectJdtVersion();
        String jdkVersion = System.getProperty("java.version", "");
        String jdkVendor = System.getProperty("java.vendor", "");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("astLevel", "AST.getJLSLatest()");
        p.put("resolveBindings", config != null && config.resolveBindings);
        p.put("bindingsRecovery", true);
        p.put("statementsRecovery", true);
        p.put("includeRunningVMBootclasspath", true);
        p.put("release", config == null ? null : (blankToNull(config.release)));
        p.put("classpathEntries", paths(config == null ? null : config.classpath));
        p.put("sourcepathEntries", paths(config == null ? null : config.sourcepath));
        Map<String, Object> configuration = new LinkedHashMap<>();
        if (provenance != null) {
            provenance.source().ifPresent(path -> configuration.put("source", path.toString()));
            provenance.sourceSha256().ifPresent(hash -> configuration.put("sourceSha256", hash));
            configuration.put("effectiveSha256", provenance.effectiveSha256());
            configuration.put("cliOverrides", provenance.cliOverrides());
            configuration.put("effectiveToml", provenance.effectiveToml());
        }
        return new BaselineMetadata(toolVersion, jdt, jdkVersion, jdkVendor, p, configuration);
    }

    private static String detectJdtVersion() {
        try {
            Package pkg = org.eclipse.jdt.core.JavaCore.class.getPackage();
            if (pkg != null) {
                String v = pkg.getImplementationVersion();
                if (v != null && !v.isBlank()) return v;
            }
        } catch (Exception ignored) {
        }
        return "3.47.0";
    }

    static String detectToolVersion() {
        Package pkg = BaselineMetadata.class.getPackage();
        if (pkg != null) {
            String version = pkg.getImplementationVersion();
            if (version != null && !version.isBlank()) return version;
        }
        String testVersion = System.getProperty("javaMetricsProjectVersion");
        if (testVersion != null && !testVersion.isBlank()) return testVersion;
        return "development";
    }

    private static Object blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static List<String> paths(List<Path> list) {
        List<String> out = new ArrayList<>();
        if (list == null) return out;
        for (Path p : list) {
            out.add(p.toAbsolutePath().normalize().toString());
        }
        return out;
    }
}
