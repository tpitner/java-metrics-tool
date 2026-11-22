// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.EnumSet;

/**
 * Common base class for declared members (methods, constructors, fields, enum constants).
 */
public abstract class Member {
    public final String id;
    public final String bindingKey;
    public final Compound owner;
    public final String name;
    public final EnumSet<Modifier> modifiers;
    /** Declared type (return/field/owning enum type), may be null for constructors. */
    public final SymbolRef declaredType;
    public final MemberKind memberKind;
    public final boolean resolved;
    /** Exact source range for this declaration when it originates in source. */
    public SourceSpan sourceSpan;

    /** Compatibility: text form of declared type. */
    public final String javaType;

    protected Member(String id, String bindingKey, Compound owner, String name, EnumSet<Modifier> modifiers,
                     SymbolRef declaredType, MemberKind memberKind, boolean resolved) {
        this.id = id;
        this.bindingKey = bindingKey;
        this.owner = owner;
        this.name = name;
        this.modifiers = modifiers == null ? EnumSet.noneOf(Modifier.class) : modifiers;
        this.declaredType = declaredType;
        this.memberKind = memberKind;
        this.resolved = resolved;
        this.javaType = declaredType == null ? null : declaredType.displayName;
    }
}
