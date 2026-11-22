// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

import static cz.utulnykoutek.metrics.util.Strings.ensureNonBlank;
import static cz.utulnykoutek.metrics.util.Strings.ensureNonNull;

import cz.utulnykoutek.metrics.metrics.definition.MetricScope;

/** Stable reference to the single program element that is being measured. */
public record SubjectRef(MetricScope scope, String id) implements Comparable<SubjectRef> {
    public SubjectRef {
        ensureNonNull(scope, "scope");
        ensureNonBlank(id, "Subject ID must not be blank");
    }

    @Override
    public int compareTo(SubjectRef other) {
        ensureNonNull(other, "other");
        int scopeComparison = scope.serializedName().compareTo(other.scope.serializedName());
        return scopeComparison != 0 ? scopeComparison : id.compareTo(other.id);
    }
}
