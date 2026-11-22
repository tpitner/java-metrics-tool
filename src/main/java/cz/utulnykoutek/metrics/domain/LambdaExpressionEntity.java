// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.Objects;

/** Lambda expression with stable source-order identity and its immediate enclosing subject. */
public record LambdaExpressionEntity(
        String id,
        TypeCompound owner,
        String enclosingSubjectId,
        SourceSpan sourceSpan) {
    public LambdaExpressionEntity {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Lambda ID must not be blank");
        Objects.requireNonNull(owner, "owner");
        if (enclosingSubjectId == null || enclosingSubjectId.isBlank()) {
            throw new IllegalArgumentException("Lambda enclosing subject ID must not be blank");
        }
        Objects.requireNonNull(sourceSpan, "sourceSpan");
    }
}
