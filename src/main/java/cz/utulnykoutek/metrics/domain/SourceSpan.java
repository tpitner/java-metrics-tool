// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.nio.file.Path;
import java.util.Objects;

/** Exact half-open character range of an entity in a normalized source file. */
public record SourceSpan(Path sourceFile, int startOffset, int endOffset) {
    public SourceSpan {
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile")
                .toAbsolutePath().normalize();
        if (startOffset < 0) throw new IllegalArgumentException("startOffset must be >= 0");
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must be >= startOffset");
        }
    }
}
