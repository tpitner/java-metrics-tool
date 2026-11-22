// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import cz.utulnykoutek.metrics.metrics.JavaMetricsProvider;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Applies defaults and CLI precedence, resolves paths and validates cross-field constraints. */
public final class EffectiveConfigResolver {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private EffectiveConfigResolver() {
    }

    public static EffectiveConfig resolve(
            LoadedToolConfig loaded,
            CliOverrides cli,
            Path workingDirectory) throws ConfigException {
        ToolConfig raw = loaded.config();
        Path cwd = workingDirectory.toAbsolutePath().normalize();
        Path base = loaded.baseDirectory();
        List<String> errors = new ArrayList<>();

        if (raw.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            errors.add("Unsupported schema-version " + raw.schemaVersion()
                    + "; supported version is " + SUPPORTED_SCHEMA_VERSION);
        }

        ToolConfig.ProjectSection projectRaw = raw.project();
        List<Path> inputs = cli.hasInputs()
                ? resolvePaths(cwd, cli.inputs())
                : resolveStrings(base, projectRaw.inputs().orElse(List.of()));
        Optional<Path> pom = cli.pom().map(path -> resolve(cwd, path));
        if (pom.isEmpty()) pom = projectRaw.pom().map(path -> resolve(base, Path.of(path)));
        boolean recursive = cli.recursive().orElse(projectRaw.recursive().orElse(true));
        boolean includeTests = cli.includeTests().orElse(projectRaw.includeTests().orElse(false));
        boolean compile = cli.compile().orElse(projectRaw.compile().orElse(true));
        List<Path> sourcepath = cli.hasSourcepath()
                ? resolvePaths(cwd, cli.sourcepath())
                : resolveStrings(base, projectRaw.sourcepath().orElse(List.of()));
        List<Path> classpath = cli.hasClasspath()
                ? resolvePaths(cwd, cli.classpath())
                : resolveStrings(base, projectRaw.classpath().orElse(List.of()));
        Optional<String> release = cli.release().or(() -> projectRaw.release());
        ProjectConfig project = new ProjectConfig(
                inputs, pom, recursive, includeTests, compile, sourcepath, classpath, release);

        ToolConfig.OutputSection outputRaw = raw.output();
        String format = cli.format().orElse(outputRaw.format().orElse("text")).toLowerCase(Locale.ROOT);
        String destinationValue = outputRaw.destination().orElse("-");
        Optional<Path> destination = "-".equals(destinationValue)
                ? Optional.empty()
                : Optional.of(resolve(base, Path.of(destinationValue)));
        boolean retainArtifacts = outputRaw.retainProviderArtifacts().orElse(false);
        Path artifactsDirectory = resolve(base, Path.of(
                outputRaw.providerArtifactsDirectory().orElse("target/java-metrics/providers")));
        OutputConfig output = new OutputConfig(format, destination, retainArtifacts, artifactsDirectory);

        ToolConfig.JavaSection javaRaw = raw.providers().java();
        boolean javaEnabled = javaRaw.enabled().orElse(true);
        Set<BuiltInMetricDefinition> implemented = new JavaMetricsProvider().definitions();
        Set<BuiltInMetricDefinition> selectedJavaMetrics = javaRaw.metrics().isEmpty()
                ? implemented
                : parseJavaMetrics(javaRaw.metrics().orElseThrow(), implemented, errors);
        JavaProviderConfig java = new JavaProviderConfig(javaEnabled, selectedJavaMetrics);

        ToolConfig.CkSection ckRaw = raw.providers().ck();
        boolean ckEnabled = ckRaw.enabled().orElse(false);
        Optional<Path> ckJar = ckRaw.jar().map(path -> resolve(base, Path.of(path)));
        String javaExecutable = ckRaw.javaExecutable().orElse("java");
        boolean useJars = ckRaw.useJars().orElse(true);
        long partitionLong = ckRaw.maxFilesPerPartition().orElse(0L);
        long timeoutLong = ckRaw.timeoutSeconds().orElse(600L);
        int partition = checkedInt(partitionLong, "providers.ck.max-files-per-partition", errors);
        boolean variablesAndFields = ckRaw.variablesAndFields().orElse(true);
        List<String> ignored = ckRaw.ignoredDirectories().orElse(List.of(".git", "target", "build"));
        boolean retainRawCsv = ckRaw.retainRawCsv().orElse(true);
        CkProviderConfig ck = new CkProviderConfig(
                ckEnabled, ckJar, javaExecutable, useJars, partition, variablesAndFields,
                ignored, Duration.ofSeconds(timeoutLong), retainRawCsv);
        ProviderConfigs providers = new ProviderConfigs(java, ck);

        errors.addAll(ConfigValidator.validate(project, output, providers));
        if (!errors.isEmpty()) throw new ConfigException(errors);

        String effectiveToml = EffectiveConfigTomlWriter.render(
                raw.schemaVersion(), project, output, providers);
        ConfigurationProvenance provenance = new ConfigurationProvenance(
                loaded.source(),
                loaded.sourceSha256(),
                effectiveToml,
                ConfigHashes.sha256(effectiveToml.getBytes(StandardCharsets.UTF_8)),
                cli.appliedKeys());
        return new EffectiveConfig(raw.schemaVersion(), project, output, providers, provenance);
    }

    private static Set<BuiltInMetricDefinition> parseJavaMetrics(
            List<String> names,
            Set<BuiltInMetricDefinition> implemented,
            List<String> errors) {
        EnumSet<BuiltInMetricDefinition> selected = EnumSet.noneOf(BuiltInMetricDefinition.class);
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                errors.add("Duplicate providers.java metric: " + name);
                continue;
            }
            BuiltInMetricDefinition definition = Arrays.stream(BuiltInMetricDefinition.values())
                    .filter(candidate -> candidate.id().serializedName().equals(name))
                    .findFirst()
                    .orElse(null);
            if (definition == null) {
                errors.add("Unknown providers.java metric: " + name);
            } else if (!implemented.contains(definition)) {
                errors.add("Built-in metric is defined but not implemented: " + name);
            } else {
                selected.add(definition);
            }
        }
        return selected;
    }

    private static int checkedInt(long value, String path, List<String> errors) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            errors.add(path + " is outside the 32-bit integer range");
            return 0;
        }
        return (int) value;
    }

    private static List<Path> resolveStrings(Path base, List<String> paths) {
        return paths.stream().map(Path::of).map(path -> resolve(base, path)).toList();
    }

    private static List<Path> resolvePaths(Path base, List<Path> paths) {
        return paths.stream().map(path -> resolve(base, path)).toList();
    }

    private static Path resolve(Path base, Path path) {
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }
}
