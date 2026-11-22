// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Complete immutable cohesion evidence collected for one declared type. */
public record TypeCohesionEvidence(
        TypeCompound owner,
        Set<Field> declaredInstanceFields,
        List<MethodCohesionEvidence> methods) {

    public TypeCohesionEvidence {
        Objects.requireNonNull(owner, "owner");
        declaredInstanceFields = immutableFields(owner, declaredInstanceFields);
        methods = methods == null ? List.of() : List.copyOf(methods);
        Set<Method> seen = new LinkedHashSet<>();
        for (MethodCohesionEvidence evidence : methods) {
            Objects.requireNonNull(evidence, "methods contains null");
            if (evidence.method().owner != owner) {
                throw new IllegalArgumentException("Evidence method must be declared by the evidence owner");
            }
            if (!seen.add(evidence.method())) {
                throw new IllegalArgumentException("Duplicate cohesion evidence for method "
                        + evidence.method().id);
            }
            if (!declaredInstanceFields.containsAll(evidence.accessedInstanceFields())) {
                throw new IllegalArgumentException("Method evidence refers to an undeclared instance field");
            }
        }
    }

    /** Eligible graph vertices in declaration order. */
    public List<MethodCohesionEvidence> eligibleMethods() {
        return methods.stream().filter(MethodCohesionEvidence::eligible).toList();
    }

    /** Whether every method's potentially cohesion-relevant references resolved. */
    public boolean complete() {
        return methods.stream().allMatch(MethodCohesionEvidence::complete);
    }

    private static Set<Field> immutableFields(TypeCompound owner, Set<Field> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<Field> copy = new LinkedHashSet<>();
        for (Field field : values) {
            Objects.requireNonNull(field, "declaredInstanceFields contains null");
            if (field.owner != owner) {
                throw new IllegalArgumentException("Instance field must be declared by the evidence owner");
            }
            if (field.modifiers.contains(Modifier.STATIC)) {
                throw new IllegalArgumentException("Static field is not a declared instance field");
            }
            copy.add(field);
        }
        return Collections.unmodifiableSet(copy);
    }
}
