// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A declared type (class, interface, enum, record, annotation, local/anonymous).
 */
public class TypeCompound extends Compound {
    public SymbolRef superclass;
    public final List<SymbolRef> interfaces = new ArrayList<>();
    public final EnumSet<Modifier> modifiers;
    public final NestingKind nesting;
    public final List<TypeParameterDecl> typeParameters = new ArrayList<>();
    public final List<DependencyEdge> dependencyEdges = new ArrayList<>();
    public final List<InitializerBlock> initializers = new ArrayList<>();
    public final List<LambdaExpressionEntity> lambdas = new ArrayList<>();
    public final List<CyclomaticComplexityEvidence> cyclomaticComplexityEvidence = new ArrayList<>();
    /** Null until cohesion evidence has been collected for this type. */
    public TypeCohesionEvidence cohesionEvidence;
    public String declaredInMemberId;
    public String bindingKey;

    /** Derived/compatibility view for preview and legacy consumers. */
    public final Map<String, List<String>> dependencies = new LinkedHashMap<>();

    public TypeCompound(String id, String symbolId, CompoundKind kind, String name, String qualifiedName,
                        Compound parent, EnumSet<Modifier> modifiers, NestingKind nesting,
                        boolean resolved, String bindingKey) {
        super(id, symbolId, kind, name, qualifiedName, parent, resolved);
        this.modifiers = modifiers == null ? EnumSet.noneOf(Modifier.class) : modifiers;
        this.nesting = nesting;
        this.bindingKey = bindingKey;
    }

    /** Compatibility constructor. */
    public TypeCompound(String id, CompoundKind kind, String name, String qualifiedName,
                        Compound parent, EnumSet<Modifier> modifiers, NestingKind nesting) {
        this(id, id, kind, name, qualifiedName, parent, modifiers, nesting, false, null);
    }

    public boolean isInner() {
        return nesting == NestingKind.INNER_MEMBER;
    }

    /** Rebuild derived dependency summary from dependencyEdges. */
    public void rebuildDependencySummary() {
        dependencies.clear();
        for (DependencyEdge e : dependencyEdges) {
            if (e.to == null || e.to.qualifiedName == null) continue;
            String cat = switch (e.to.kindHint) {
                case INTERFACE -> "interfaces";
                case ENUM -> "enums";
                case ANNOTATION -> "annotations";
                default -> "classes";
            };
            dependencies.computeIfAbsent(cat, k -> new ArrayList<>());
            List<String> list = dependencies.get(cat);
            String name = e.to.qualifiedName;
            if (!list.contains(name)) list.add(name);
        }
    }
}
