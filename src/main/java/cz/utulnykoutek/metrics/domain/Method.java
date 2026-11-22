// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A declared method.
 */
public class Method extends Member {
    public final SymbolRef returnType;
    public final List<Parameter> parameters;
    public final List<SymbolRef> thrownExceptions;

    public Method(String id, String bindingKey, Compound owner, String name, EnumSet<Modifier> modifiers,
                  SymbolRef returnType, List<Parameter> parameters, List<SymbolRef> thrownExceptions, boolean resolved) {
        super(id, bindingKey, owner, name, modifiers, returnType, MemberKind.METHOD, resolved);
        this.returnType = returnType;
        this.parameters = parameters == null ? new ArrayList<>() : parameters;
        this.thrownExceptions = thrownExceptions == null ? new ArrayList<>() : thrownExceptions;
    }
}