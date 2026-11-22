// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.domain.CohesionMethodExclusion;
import cz.utulnykoutek.metrics.domain.CompoundKind;
import cz.utulnykoutek.metrics.domain.Field;
import cz.utulnykoutek.metrics.domain.Method;
import cz.utulnykoutek.metrics.domain.MethodCohesionEvidence;
import cz.utulnykoutek.metrics.domain.Modifier;
import cz.utulnykoutek.metrics.domain.NestingKind;
import cz.utulnykoutek.metrics.domain.TypeCohesionEvidence;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CohesionEvidenceTest {
    @Test
    void keepsObjectReferencesInImmutableValidatedEvidence() {
        TypeCompound owner = type("sample.Subject");
        Field state = new Field("field:state", null, owner, "state",
                EnumSet.noneOf(Modifier.class), null, true);
        Method first = method(owner, "first");
        Method second = method(owner, "second");

        LinkedHashSet<Field> accessed = new LinkedHashSet<>(Set.of(state));
        LinkedHashSet<Method> called = new LinkedHashSet<>(Set.of(second));
        MethodCohesionEvidence firstEvidence = new MethodCohesionEvidence(
                first, Set.of(), accessed, called, 0, 0);
        MethodCohesionEvidence secondEvidence = new MethodCohesionEvidence(
                second, Set.of(CohesionMethodExclusion.TRIVIAL_ACCESSOR),
                Set.of(state), Set.of(), 1, 0);
        TypeCohesionEvidence evidence = new TypeCohesionEvidence(
                owner, Set.of(state), List.of(firstEvidence, secondEvidence));

        accessed.clear();
        called.clear();
        assertEquals(Set.of(state), firstEvidence.accessedInstanceFields());
        assertEquals(Set.of(second), firstEvidence.calledDeclaredMethods());
        assertTrue(firstEvidence.eligible());
        assertFalse(secondEvidence.eligible());
        assertFalse(evidence.complete());
        assertEquals(List.of(firstEvidence), evidence.eligibleMethods());
        assertThrows(UnsupportedOperationException.class,
                () -> firstEvidence.accessedInstanceFields().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> evidence.methods().clear());
    }

    @Test
    void rejectsStaticForeignAndDuplicateEvidence() {
        TypeCompound owner = type("sample.Subject");
        TypeCompound foreignOwner = type("sample.Foreign");
        Method method = method(owner, "work");
        Field staticField = new Field("field:static", null, owner, "state",
                EnumSet.of(Modifier.STATIC), null, true);
        Field foreignField = new Field("field:foreign", null, foreignOwner, "state",
                EnumSet.noneOf(Modifier.class), null, true);

        assertThrows(IllegalArgumentException.class, () -> new MethodCohesionEvidence(
                method, Set.of(), Set.of(staticField), Set.of(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MethodCohesionEvidence(
                method, Set.of(), Set.of(foreignField), Set.of(), 0, 0));
        MethodCohesionEvidence valid = new MethodCohesionEvidence(
                method, Set.of(), Set.of(), Set.of(), 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new TypeCohesionEvidence(
                owner, Set.of(), List.of(valid, valid)));
        assertThrows(IllegalArgumentException.class, () -> new MethodCohesionEvidence(
                method, Set.of(), Set.of(), Set.of(), -1, 0));
    }

    private static TypeCompound type(String qualifiedName) {
        return new TypeCompound("type:" + qualifiedName, "symbol:" + qualifiedName,
                CompoundKind.CLASS, qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1),
                qualifiedName, null, EnumSet.noneOf(Modifier.class),
                NestingKind.TOP_LEVEL, true, null);
    }

    private static Method method(TypeCompound owner, String name) {
        return new Method("method:" + name, null, owner, name,
                EnumSet.noneOf(Modifier.class), null, List.of(), List.of(), true);
    }
}
