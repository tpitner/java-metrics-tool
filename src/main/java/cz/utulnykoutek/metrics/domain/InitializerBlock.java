// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.Objects;

/** Declared instance or static initializer with its own measurement identity. */
public record InitializerBlock(
        String id,
        TypeCompound owner,
        boolean isStatic,
        SourceSpan sourceSpan) {
    public InitializerBlock {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Initializer ID must not be blank");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(sourceSpan, "sourceSpan");
    }
}
