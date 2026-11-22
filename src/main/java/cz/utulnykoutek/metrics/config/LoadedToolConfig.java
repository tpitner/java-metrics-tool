// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Parsed TOML values together with source identity and path base. */
public record LoadedToolConfig(
        ToolConfig config,
        Optional<Path> source,
        Optional<String> sourceSha256,
        Path baseDirectory) {

    public LoadedToolConfig {
        Objects.requireNonNull(config, "config");
        source = Objects.requireNonNull(source, "source");
        sourceSha256 = Objects.requireNonNull(sourceSha256, "sourceSha256");
        Objects.requireNonNull(baseDirectory, "baseDirectory");
    }
}
