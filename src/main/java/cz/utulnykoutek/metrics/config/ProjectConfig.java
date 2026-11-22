// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Fully resolved source and build inputs for one analysis. */
public record ProjectConfig(
        List<Path> inputs,
        Optional<Path> pom,
        boolean recursive,
        boolean includeTests,
        boolean compile,
        List<Path> sourcepath,
        List<Path> classpath,
        Optional<String> release) {

    public ProjectConfig {
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        pom = Objects.requireNonNull(pom, "pom");
        sourcepath = List.copyOf(Objects.requireNonNull(sourcepath, "sourcepath"));
        classpath = List.copyOf(Objects.requireNonNull(classpath, "classpath"));
        release = Objects.requireNonNull(release, "release")
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }
}
