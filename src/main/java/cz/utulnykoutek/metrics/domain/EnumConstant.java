// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.EnumSet;

public final class EnumConstant extends Member {
    public EnumConstant(String id, String bindingKey, Compound owner, String name,
                        EnumSet<Modifier> modifiers, boolean resolved, SymbolRef declaredType) {
        super(id, bindingKey, owner, name, modifiers, declaredType, MemberKind.ENUM_CONSTANT, resolved);
    }
}
