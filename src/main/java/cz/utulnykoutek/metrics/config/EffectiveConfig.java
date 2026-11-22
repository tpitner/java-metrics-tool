// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.util.Objects;

/** Fully defaulted, overridden, validated and path-resolved tool configuration. */
public record EffectiveConfig(
        int schemaVersion,
        ProjectConfig project,
        OutputConfig output,
        ProviderConfigs providers,
        ConfigurationProvenance provenance) {

    public EffectiveConfig {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(provenance, "provenance");
    }
}
