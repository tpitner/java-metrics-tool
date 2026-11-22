// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

/** Deterministic TOML representation used for inspection and provenance hashing. */
public final class EffectiveConfigTomlWriter {
    private EffectiveConfigTomlWriter() {
    }

    public static String render(
            int schemaVersion,
            ProjectConfig project,
            OutputConfig output,
            ProviderConfigs providers) {
        StringBuilder result = new StringBuilder();
        result.append("schema-version = ").append(schemaVersion).append("\n\n");
        result.append("[project]\n");
        result.append("inputs = ").append(paths(project.inputs())).append("\n");
        project.pom().ifPresent(path -> result.append("pom = ").append(quote(path.toString())).append("\n"));
        result.append("recursive = ").append(project.recursive()).append("\n");
        result.append("include-tests = ").append(project.includeTests()).append("\n");
        result.append("compile = ").append(project.compile()).append("\n");
        result.append("sourcepath = ").append(paths(project.sourcepath())).append("\n");
        result.append("classpath = ").append(paths(project.classpath())).append("\n");
        project.release().ifPresent(value -> result.append("release = ").append(quote(value)).append("\n"));

        result.append("\n[output]\n");
        result.append("format = ").append(quote(output.format())).append("\n");
        result.append("destination = ")
                .append(quote(output.destination().map(Path::toString).orElse("-"))).append("\n");
        result.append("retain-provider-artifacts = ").append(output.retainProviderArtifacts()).append("\n");
        result.append("provider-artifacts-directory = ")
                .append(quote(output.providerArtifactsDirectory().toString())).append("\n");

        JavaProviderConfig java = providers.java();
        result.append("\n[providers.java]\n");
        result.append("enabled = ").append(java.enabled()).append("\n");
        result.append("metrics = [")
                .append(java.metrics().stream()
                        .sorted(Comparator.comparing(definition -> definition.id().serializedName()))
                        .map(definition -> quote(definition.id().serializedName()))
                        .collect(Collectors.joining(", ")))
                .append("]\n");

        CkProviderConfig ck = providers.ck();
        result.append("\n[providers.ck]\n");
        result.append("enabled = ").append(ck.enabled()).append("\n");
        ck.jar().ifPresent(path -> result.append("jar = ").append(quote(path.toString())).append("\n"));
        result.append("java-executable = ").append(quote(ck.javaExecutable())).append("\n");
        result.append("use-jars = ").append(ck.useJars()).append("\n");
        result.append("max-files-per-partition = ").append(ck.maxFilesPerPartition()).append("\n");
        result.append("variables-and-fields = ").append(ck.variablesAndFields()).append("\n");
        result.append("ignored-directories = ").append(strings(ck.ignoredDirectories())).append("\n");
        result.append("timeout-seconds = ").append(ck.timeout().toSeconds()).append("\n");
        result.append("retain-raw-csv = ").append(ck.retainRawCsv()).append("\n");
        return result.toString();
    }

    private static String paths(Collection<Path> paths) {
        return strings(paths.stream().map(Path::toString).toList());
    }

    private static String strings(Collection<String> values) {
        return values.stream().map(EffectiveConfigTomlWriter::quote)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
