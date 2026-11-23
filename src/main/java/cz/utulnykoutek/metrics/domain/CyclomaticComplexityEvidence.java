// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.Objects;

/** Parser-derived decision evidence for one executable, excluding nested executable scopes. */
public record CyclomaticComplexityEvidence(
        String subjectId,
        ExecutableKind kind,
        boolean hasBody,
        int decisionPoints) {
    public CyclomaticComplexityEvidence {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("Executable subject ID must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        if (decisionPoints < 0) throw new IllegalArgumentException("Decision points must not be negative");
        if (!hasBody && decisionPoints != 0) {
            throw new IllegalArgumentException("Bodyless executable cannot contain decision points");
        }
    }

    public int complexity() {
        if (!hasBody) throw new IllegalStateException("Bodyless executable has no cyclomatic complexity");
        return 1 + decisionPoints;
    }
}
