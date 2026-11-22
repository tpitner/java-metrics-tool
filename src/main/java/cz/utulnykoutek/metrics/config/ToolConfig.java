// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Typed values explicitly present in a TOML file, before defaults and CLI overrides. */
public record ToolConfig(
        int schemaVersion,
        ProjectSection project,
        OutputSection output,
        ProviderSections providers) {

    public ToolConfig {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(providers, "providers");
    }

    public static ToolConfig empty() {
        return new ToolConfig(1, ProjectSection.empty(), OutputSection.empty(), ProviderSections.empty());
    }

    public record ProjectSection(
            Optional<List<String>> inputs,
            Optional<String> pom,
            Optional<Boolean> recursive,
            Optional<Boolean> includeTests,
            Optional<Boolean> compile,
            Optional<List<String>> sourcepath,
            Optional<List<String>> classpath,
            Optional<String> release) {

        public ProjectSection {
            inputs = immutableOptionalList(inputs);
            pom = required(pom);
            recursive = required(recursive);
            includeTests = required(includeTests);
            compile = required(compile);
            sourcepath = immutableOptionalList(sourcepath);
            classpath = immutableOptionalList(classpath);
            release = required(release);
        }

        static ProjectSection empty() {
            return new ProjectSection(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public record OutputSection(
            Optional<String> format,
            Optional<String> destination,
            Optional<Boolean> retainProviderArtifacts,
            Optional<String> providerArtifactsDirectory) {

        public OutputSection {
            format = required(format);
            destination = required(destination);
            retainProviderArtifacts = required(retainProviderArtifacts);
            providerArtifactsDirectory = required(providerArtifactsDirectory);
        }

        static OutputSection empty() {
            return new OutputSection(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public record ProviderSections(JavaSection java, CkSection ck) {
        public ProviderSections {
            Objects.requireNonNull(java, "java");
            Objects.requireNonNull(ck, "ck");
        }

        static ProviderSections empty() {
            return new ProviderSections(JavaSection.empty(), CkSection.empty());
        }
    }

    public record JavaSection(
            Optional<Boolean> enabled,
            Optional<List<String>> metrics) {

        public JavaSection {
            enabled = required(enabled);
            metrics = immutableOptionalList(metrics);
        }

        static JavaSection empty() {
            return new JavaSection(Optional.empty(), Optional.empty());
        }
    }

    public record CkSection(
            Optional<Boolean> enabled,
            Optional<String> jar,
            Optional<String> javaExecutable,
            Optional<Boolean> useJars,
            Optional<Long> maxFilesPerPartition,
            Optional<Boolean> variablesAndFields,
            Optional<List<String>> ignoredDirectories,
            Optional<Long> timeoutSeconds,
            Optional<Boolean> retainRawCsv) {

        public CkSection {
            enabled = required(enabled);
            jar = required(jar);
            javaExecutable = required(javaExecutable);
            useJars = required(useJars);
            maxFilesPerPartition = required(maxFilesPerPartition);
            variablesAndFields = required(variablesAndFields);
            ignoredDirectories = immutableOptionalList(ignoredDirectories);
            timeoutSeconds = required(timeoutSeconds);
            retainRawCsv = required(retainRawCsv);
        }

        static CkSection empty() {
            return new CkSection(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    private static <T> Optional<T> required(Optional<T> optional) {
        return Objects.requireNonNull(optional, "optional");
    }

    private static <T> Optional<List<T>> immutableOptionalList(Optional<List<T>> optional) {
        Objects.requireNonNull(optional, "optional");
        return optional.map(List::copyOf);
    }
}
