// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import static cz.utulnykoutek.metrics.util.Strings.ensureNonBlank;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Provenance of one concrete provider execution. */
public record ProviderRunMetadata(
        String providerId,
        String providerRunId,
        String version,
        Map<String, String> configuration) {

    public ProviderRunMetadata {
        ensureNonBlank(providerId, "Provider ID must not be blank");
        ensureNonBlank(providerRunId, "Provider run ID must not be blank");
        ensureNonBlank(version, "Provider version must not be blank");
        configuration = Collections.unmodifiableMap(
                new TreeMap<>(Objects.requireNonNull(configuration, "configuration")));
    }
}
