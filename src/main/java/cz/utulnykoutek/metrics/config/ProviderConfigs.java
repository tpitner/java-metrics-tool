// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.util.Objects;

/** Typed configuration of all known measurement providers. */
public record ProviderConfigs(
        JavaProviderConfig java,
        CkProviderConfig ck) {

    public ProviderConfigs {
        Objects.requireNonNull(java, "java");
        Objects.requireNonNull(ck, "ck");
    }
}
