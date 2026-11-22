// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Output and provider-artifact policy. */
public record OutputConfig(
        String format,
        Optional<Path> destination,
        boolean retainProviderArtifacts,
        Path providerArtifactsDirectory) {

    public OutputConfig {
        format = Objects.requireNonNull(format, "format");
        destination = Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(providerArtifactsDirectory, "providerArtifactsDirectory");
    }

    public boolean writesToStdout() {
        return destination.isEmpty();
    }
}
