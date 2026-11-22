// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A declared constructor.
 */
public class Constructor extends Member {
    public final List<Parameter> parameters;
    public final List<SymbolRef> thrownExceptions;

    public Constructor(String id, String bindingKey, Compound owner, String name, EnumSet<Modifier> modifiers,
                       List<Parameter> parameters, List<SymbolRef> thrownExceptions, boolean resolved) {
        super(id, bindingKey, owner, name, modifiers, null, MemberKind.CONSTRUCTOR, resolved);
        this.parameters = parameters == null ? new ArrayList<>() : parameters;
        this.thrownExceptions = thrownExceptions == null ? new ArrayList<>() : thrownExceptions;
    }
}