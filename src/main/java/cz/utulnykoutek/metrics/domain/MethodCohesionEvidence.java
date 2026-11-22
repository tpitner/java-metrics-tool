// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Auditable field-access and intra-type call evidence collected for one
 * directly declared method.
 */
public record MethodCohesionEvidence(
        Method method,
        Set<CohesionMethodExclusion> exclusions,
        Set<Field> accessedInstanceFields,
        Set<Method> calledDeclaredMethods,
        int unresolvedFieldAccessCount,
        int unresolvedIntraTypeCallCount) {

    public MethodCohesionEvidence {
        Objects.requireNonNull(method, "method");
        exclusions = immutableExclusions(exclusions);
        accessedInstanceFields = immutableSet(accessedInstanceFields, "accessedInstanceFields");
        calledDeclaredMethods = immutableSet(calledDeclaredMethods, "calledDeclaredMethods");
        if (unresolvedFieldAccessCount < 0) {
            throw new IllegalArgumentException("unresolvedFieldAccessCount must be >= 0");
        }
        if (unresolvedIntraTypeCallCount < 0) {
            throw new IllegalArgumentException("unresolvedIntraTypeCallCount must be >= 0");
        }
        for (Field field : accessedInstanceFields) {
            if (field.owner != method.owner) {
                throw new IllegalArgumentException("Accessed field must be declared by the method owner");
            }
            if (field.modifiers.contains(Modifier.STATIC)) {
                throw new IllegalArgumentException("Static field is not LCOM4 instance-field evidence");
            }
        }
        for (Method called : calledDeclaredMethods) {
            if (called.owner != method.owner) {
                throw new IllegalArgumentException("Called method must be declared by the method owner");
            }
        }
    }

    /** Whether this method is a vertex of the primary LCOM4 graph. */
    public boolean eligible() {
        return exclusions.isEmpty();
    }

    /** Whether all potentially cohesion-relevant references were resolved. */
    public boolean complete() {
        return unresolvedFieldAccessCount == 0 && unresolvedIntraTypeCallCount == 0;
    }

    private static Set<CohesionMethodExclusion> immutableExclusions(
            Set<CohesionMethodExclusion> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static <T> Set<T> immutableSet(Set<T> values, String name) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<T> copy = new LinkedHashSet<>();
        for (T value : values) copy.add(Objects.requireNonNull(value, name + " contains null"));
        return Collections.unmodifiableSet(copy);
    }
}
