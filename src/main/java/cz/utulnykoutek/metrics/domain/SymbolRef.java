// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.Objects;

/**
 * Reference to a type symbol with identity and binding completeness.
 */
public final class SymbolRef {
    public final String id;
    public final String bindingKey;
    public final String qualifiedName;
    /** Declaring package when known from bindings; empty string denotes the default package. */
    public final String packageName;
    public final String displayName;
    public final Origin origin;
    public final boolean resolved;
    public final TypeKindHint kindHint;

    public SymbolRef(String id, String bindingKey, String qualifiedName, String displayName,
                     Origin origin, boolean resolved, TypeKindHint kindHint) {
        this(id, bindingKey, qualifiedName, inferPackageName(qualifiedName), displayName,
                origin, resolved, kindHint);
    }

    public SymbolRef(String id, String bindingKey, String qualifiedName, String packageName,
                     String displayName, Origin origin, boolean resolved, TypeKindHint kindHint) {
        this.id = id;
        this.bindingKey = bindingKey;
        this.qualifiedName = qualifiedName;
        this.packageName = packageName;
        this.displayName = displayName == null ? "" : displayName;
        this.origin = origin == null ? Origin.UNRESOLVED : origin;
        this.resolved = resolved;
        this.kindHint = kindHint == null ? TypeKindHint.UNKNOWN : kindHint;
    }

    public static SymbolRef unresolved(String id, String displayName) {
        return new SymbolRef(id, null, null, displayName, Origin.UNRESOLVED, false, TypeKindHint.UNKNOWN);
    }

    private static String inferPackageName(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) return null;
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? "" : qualifiedName.substring(0, lastDot);
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymbolRef that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(displayName, that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName);
    }
}
