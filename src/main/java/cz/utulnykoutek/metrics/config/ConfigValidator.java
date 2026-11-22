// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Cross-field and filesystem validation of a fully resolved configuration. */
public final class ConfigValidator {
    private static final Set<String> OUTPUT_FORMATS =
            Set.of("text", "json", "preview", "ansi", "yaml-preview");

    private ConfigValidator() {
    }

    public static List<String> validate(
            ProjectConfig project,
            OutputConfig output,
            ProviderConfigs providers) {
        List<String> errors = new ArrayList<>();
        if (project.inputs().isEmpty() && project.pom().isEmpty()) {
            errors.add("At least one project.inputs entry or project.pom is required");
        }
        for (Path input : project.inputs()) {
            if (!Files.isRegularFile(input) && !Files.isDirectory(input)) {
                errors.add("project.inputs entry is not a file or directory: " + input);
            }
        }
        project.pom().ifPresent(path -> {
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                errors.add("project.pom is not a readable file: " + path);
            }
        });
        for (Path sourceRoot : project.sourcepath()) {
            if (!Files.isDirectory(sourceRoot)) {
                errors.add("project.sourcepath entry is not a directory: " + sourceRoot);
            }
        }
        for (Path entry : project.classpath()) {
            if (!Files.exists(entry) || !Files.isReadable(entry)) {
                errors.add("project.classpath entry is not readable: " + entry);
            }
        }

        if (!OUTPUT_FORMATS.contains(output.format())) {
            errors.add("Unsupported output.format: " + output.format());
        }
        output.destination().ifPresent(path -> {
            if (Files.isDirectory(path)) {
                errors.add("output.destination must be a file, not a directory: " + path);
            }
        });

        if (!providers.java().enabled() && !providers.ck().enabled()) {
            errors.add("At least one measurement provider must be enabled");
        }
        if (providers.java().enabled() && providers.java().metrics().isEmpty()) {
            errors.add("providers.java.metrics must not be empty when the provider is enabled");
        }

        CkProviderConfig ck = providers.ck();
        if (ck.javaExecutable().isBlank()) {
            errors.add("providers.ck.java-executable must not be blank");
        }
        if (ck.maxFilesPerPartition() < 0) {
            errors.add("providers.ck.max-files-per-partition must be >= 0");
        }
        if (ck.timeout().isZero() || ck.timeout().isNegative()) {
            errors.add("providers.ck.timeout-seconds must be > 0");
        }
        for (int i = 0; i < ck.ignoredDirectories().size(); i++) {
            if (ck.ignoredDirectories().get(i).isBlank()) {
                errors.add("providers.ck.ignored-directories[" + i + "] must not be blank");
            }
        }
        if (ck.enabled()) {
            if (!ck.variablesAndFields()) {
                errors.add("providers.ck.variables-and-fields must be true because CK TCC/LCC depend on method field usage");
            }
            if (ck.jar().isEmpty()) {
                errors.add("providers.ck.jar is required when CK is enabled");
            } else if (!Files.isRegularFile(ck.jar().orElseThrow())
                    || !Files.isReadable(ck.jar().orElseThrow())) {
                errors.add("providers.ck.jar is not a readable file: " + ck.jar().orElseThrow());
            }
        }
        return List.copyOf(errors);
    }
}
