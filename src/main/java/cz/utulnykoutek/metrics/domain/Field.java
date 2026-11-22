// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.EnumSet;

/**
 * A declared field.
 */
public class Field extends Member {
    public final SymbolRef type;

    public Field(String id, String bindingKey, Compound owner, String name, EnumSet<Modifier> modifiers,
                 SymbolRef type, boolean resolved) {
        super(id, bindingKey, owner, name, modifiers, type, MemberKind.FIELD, resolved);
        this.type = type;
    }

    /** @return field type display name. */
    public String type() {
        return type == null ? javaType : type.displayName;
    }
}