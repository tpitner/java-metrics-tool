// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Configuration of the isolated CK CLI/CSV provider. */
public record CkProviderConfig(
        boolean enabled,
        Optional<Path> jar,
        String javaExecutable,
        boolean useJars,
        int maxFilesPerPartition,
        boolean variablesAndFields,
        List<String> ignoredDirectories,
        Duration timeout,
        boolean retainRawCsv) {

    public CkProviderConfig {
        jar = Objects.requireNonNull(jar, "jar");
        javaExecutable = Objects.requireNonNull(javaExecutable, "javaExecutable");
        ignoredDirectories = List.copyOf(Objects.requireNonNull(ignoredDirectories, "ignoredDirectories"));
        Objects.requireNonNull(timeout, "timeout");
    }
}
