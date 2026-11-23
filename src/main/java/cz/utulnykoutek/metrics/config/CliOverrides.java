// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Explicit command-line values; absent options do not override TOML. */
public final class CliOverrides {
    private Path configFile;
    private final List<Path> inputs = new ArrayList<>();
    private final List<Path> sourcepath = new ArrayList<>();
    private final List<Path> classpath = new ArrayList<>();
    private Path pom;
    private String release;
    private Boolean recursive;
    private Boolean includeTests;
    private Boolean compile;
    private String format;
    private boolean validateConfig;
    private boolean printEffectiveConfig;
    private boolean help;
    private final List<String> applied = new ArrayList<>();

    private CliOverrides() {
    }

    public static CliOverrides parse(String[] args) throws ConfigException {
        CliOverrides result = new CliOverrides();
        List<String> errors = new ArrayList<>();
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                result.inputs.add(Path.of(arg));
                result.applied.add("project.inputs");
                i++;
                continue;
            }
            switch (arg) {
                case "--config" -> {
                    String value = requireValue(args, i + 1, arg, errors);
                    if (value != null) {
                        result.configFile = Path.of(value);
                        i++;
                    }
                }
                case "--sourcepath" -> {
                    String value = requireValue(args, i + 1, arg, errors);
                    if (value != null) {
                        addPathList(result.sourcepath, value);
                        i++;
                    }
                    result.applied.add("project.sourcepath");
                }
                case "--classpath" -> {
                    String value = requireValue(args, i + 1, arg, errors);
                    if (value != null) {
                        addPathList(result.classpath, value);
                        i++;
                    }
                    result.applied.add("project.classpath");
                }
                case "--release" -> {
                    result.release = requireValue(args, i + 1, arg, errors);
                    if (result.release != null) i++;
                    result.applied.add("project.release");
                }
                case "--recursive" -> {
                    result.recursive = true;
                    result.applied.add("project.recursive");
                }
                case "--no-recursive" -> {
                    result.recursive = false;
                    result.applied.add("project.recursive");
                }
                case "--pom" -> {
                    String value = requireValue(args, i + 1, arg, errors);
                    if (value != null) {
                        result.pom = Path.of(value);
                        i++;
                    }
                    result.applied.add("project.pom");
                }
                case "--include-tests" -> {
                    result.includeTests = true;
                    result.applied.add("project.include-tests");
                }
                case "--no-compile" -> {
                    result.compile = false;
                    result.applied.add("project.compile");
                }
                case "--format" -> {
                    result.format = requireValue(args, i + 1, arg, errors);
                    if (result.format != null) i++;
                    result.applied.add("output.format");
                }
                case "--validate-config" -> result.validateConfig = true;
                case "--print-effective-config" -> result.printEffectiveConfig = true;
                case "--help" -> result.help = true;
                default -> errors.add("Unknown option: " + arg);
            }
            i++;
        }
        if (!errors.isEmpty()) throw new ConfigException(errors);
        return result;
    }

    private static String requireValue(String[] args, int index, String option, List<String> errors) {
        if (index >= args.length || args[index].startsWith("--")) {
            errors.add(option + " requires a value");
            return null;
        }
        return args[index];
    }

    private static void addPathList(List<Path> target, String value) {
        for (String part : value.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!part.isBlank()) target.add(Path.of(part));
        }
    }

    public Optional<Path> configFile() { return Optional.ofNullable(configFile); }
    public List<Path> inputs() { return List.copyOf(inputs); }
    public boolean hasInputs() { return !inputs.isEmpty(); }
    public List<Path> sourcepath() { return List.copyOf(sourcepath); }
    public boolean hasSourcepath() { return applied.contains("project.sourcepath"); }
    public List<Path> classpath() { return List.copyOf(classpath); }
    public boolean hasClasspath() { return applied.contains("project.classpath"); }
    public Optional<Path> pom() { return Optional.ofNullable(pom); }
    public Optional<String> release() { return Optional.ofNullable(release); }
    public Optional<Boolean> recursive() { return Optional.ofNullable(recursive); }
    public Optional<Boolean> includeTests() { return Optional.ofNullable(includeTests); }
    public Optional<Boolean> compile() { return Optional.ofNullable(compile); }
    public Optional<String> format() { return Optional.ofNullable(format); }
    public boolean validateConfig() { return validateConfig; }
    public boolean printEffectiveConfig() { return printEffectiveConfig; }
    public boolean help() { return help; }
    public List<String> appliedKeys() { return applied.stream().distinct().sorted().toList(); }
}
