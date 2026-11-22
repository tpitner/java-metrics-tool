// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Strict TOML reader. Unknown keys and wrong value types are configuration errors. */
public final class ConfigLoader {
    private static final Set<String> ROOT_KEYS = Set.of("schema-version", "project", "output", "providers");
    private static final Set<String> PROJECT_KEYS = Set.of(
            "inputs", "pom", "recursive", "include-tests", "compile", "sourcepath", "classpath", "release");
    private static final Set<String> OUTPUT_KEYS = Set.of(
            "format", "destination", "retain-provider-artifacts", "provider-artifacts-directory");
    private static final Set<String> PROVIDER_KEYS = Set.of("java", "ck");
    private static final Set<String> JAVA_KEYS = Set.of("enabled", "metrics");
    private static final Set<String> CK_KEYS = Set.of(
            "enabled", "jar", "java-executable", "use-jars", "max-files-per-partition",
            "variables-and-fields", "ignored-directories", "timeout-seconds", "retain-raw-csv");

    private ConfigLoader() {
    }

    public static LoadedToolConfig load(Optional<Path> requestedFile, Path workingDirectory)
            throws ConfigException {
        Path cwd = workingDirectory.toAbsolutePath().normalize();
        if (requestedFile.isEmpty()) {
            return new LoadedToolConfig(ToolConfig.empty(), Optional.empty(), Optional.empty(), cwd);
        }

        Path source = resolve(cwd, requestedFile.orElseThrow());
        List<String> errors = new ArrayList<>();
        if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new ConfigException(List.of("Configuration file is not readable: " + source));
        }

        byte[] bytes;
        TomlParseResult toml;
        try {
            bytes = Files.readAllBytes(source);
            toml = Toml.parse(source);
        } catch (IOException e) {
            throw new ConfigException(List.of("Cannot read configuration " + source + ": " + e.getMessage()));
        }
        toml.errors().forEach(error -> errors.add(error.toString()));
        if (!errors.isEmpty()) throw new ConfigException(errors);

        checkKeys(toml, "", ROOT_KEYS, errors);
        long schemaVersionLong = integer(toml, "schema-version", "schema-version", errors).orElse(1L);
        int schemaVersion;
        if (schemaVersionLong < Integer.MIN_VALUE || schemaVersionLong > Integer.MAX_VALUE) {
            errors.add("schema-version is outside the 32-bit integer range");
            schemaVersion = -1;
        } else {
            schemaVersion = (int) schemaVersionLong;
        }

        TomlTable project = table(toml, "project", "project", errors).orElse(null);
        TomlTable output = table(toml, "output", "output", errors).orElse(null);
        TomlTable providers = table(toml, "providers", "providers", errors).orElse(null);
        if (project != null) checkKeys(project, "project", PROJECT_KEYS, errors);
        if (output != null) checkKeys(output, "output", OUTPUT_KEYS, errors);
        if (providers != null) checkKeys(providers, "providers", PROVIDER_KEYS, errors);

        TomlTable java = providers == null ? null : table(providers, "java", "providers.java", errors).orElse(null);
        TomlTable ck = providers == null ? null : table(providers, "ck", "providers.ck", errors).orElse(null);
        if (java != null) checkKeys(java, "providers.java", JAVA_KEYS, errors);
        if (ck != null) checkKeys(ck, "providers.ck", CK_KEYS, errors);

        ToolConfig.ProjectSection projectSection = project == null
                ? ToolConfig.ProjectSection.empty()
                : new ToolConfig.ProjectSection(
                        stringList(project, "inputs", "project.inputs", errors),
                        string(project, "pom", "project.pom", errors),
                        bool(project, "recursive", "project.recursive", errors),
                        bool(project, "include-tests", "project.include-tests", errors),
                        bool(project, "compile", "project.compile", errors),
                        stringList(project, "sourcepath", "project.sourcepath", errors),
                        stringList(project, "classpath", "project.classpath", errors),
                        string(project, "release", "project.release", errors));

        ToolConfig.OutputSection outputSection = output == null
                ? ToolConfig.OutputSection.empty()
                : new ToolConfig.OutputSection(
                        string(output, "format", "output.format", errors),
                        string(output, "destination", "output.destination", errors),
                        bool(output, "retain-provider-artifacts", "output.retain-provider-artifacts", errors),
                        string(output, "provider-artifacts-directory", "output.provider-artifacts-directory", errors));

        ToolConfig.JavaSection javaSection = java == null
                ? ToolConfig.JavaSection.empty()
                : new ToolConfig.JavaSection(
                        bool(java, "enabled", "providers.java.enabled", errors),
                        stringList(java, "metrics", "providers.java.metrics", errors));

        ToolConfig.CkSection ckSection = ck == null
                ? ToolConfig.CkSection.empty()
                : new ToolConfig.CkSection(
                        bool(ck, "enabled", "providers.ck.enabled", errors),
                        string(ck, "jar", "providers.ck.jar", errors),
                        string(ck, "java-executable", "providers.ck.java-executable", errors),
                        bool(ck, "use-jars", "providers.ck.use-jars", errors),
                        integer(ck, "max-files-per-partition", "providers.ck.max-files-per-partition", errors),
                        bool(ck, "variables-and-fields", "providers.ck.variables-and-fields", errors),
                        stringList(ck, "ignored-directories", "providers.ck.ignored-directories", errors),
                        integer(ck, "timeout-seconds", "providers.ck.timeout-seconds", errors),
                        bool(ck, "retain-raw-csv", "providers.ck.retain-raw-csv", errors));

        if (!errors.isEmpty()) throw new ConfigException(errors);
        ToolConfig config = new ToolConfig(
                schemaVersion,
                projectSection,
                outputSection,
                new ToolConfig.ProviderSections(javaSection, ckSection));
        Path base = Optional.ofNullable(source.getParent()).orElse(cwd);
        return new LoadedToolConfig(
                config,
                Optional.of(source),
                Optional.of(ConfigHashes.sha256(bytes)),
                base);
    }

    private static void checkKeys(TomlTable table, String prefix, Set<String> allowed, List<String> errors) {
        for (String key : table.keySet()) {
            if (!allowed.contains(key)) {
                errors.add("Unknown configuration key: " + (prefix.isEmpty() ? key : prefix + "." + key));
            }
        }
    }

    private static Optional<TomlTable> table(
            TomlTable parent, String key, String path, List<String> errors) {
        Object value = parent.get(key);
        if (value == null) return Optional.empty();
        if (value instanceof TomlTable result) return Optional.of(result);
        errors.add(path + " must be a table");
        return Optional.empty();
    }

    private static Optional<String> string(
            TomlTable table, String key, String path, List<String> errors) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (value instanceof String result) return Optional.of(result);
        errors.add(path + " must be a string");
        return Optional.empty();
    }

    private static Optional<Boolean> bool(
            TomlTable table, String key, String path, List<String> errors) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (value instanceof Boolean result) return Optional.of(result);
        errors.add(path + " must be a boolean");
        return Optional.empty();
    }

    private static Optional<Long> integer(
            TomlTable table, String key, String path, List<String> errors) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (value instanceof Long result) return Optional.of(result);
        errors.add(path + " must be an integer");
        return Optional.empty();
    }

    private static Optional<List<String>> stringList(
            TomlTable table, String key, String path, List<String> errors) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof TomlArray array)) {
            errors.add(path + " must be an array of strings");
            return Optional.empty();
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (item instanceof String string) {
                values.add(string);
            } else {
                errors.add(path + "[" + i + "] must be a string");
            }
        }
        return Optional.of(List.copyOf(values));
    }

    private static Path resolve(Path base, Path path) {
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }
}
