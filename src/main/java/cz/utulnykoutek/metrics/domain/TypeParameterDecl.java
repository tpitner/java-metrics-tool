// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.ArrayList;
import java.util.List;

public final class TypeParameterDecl {
    public final String name;
    public final List<SymbolRef> bounds = new ArrayList<>();

    public TypeParameterDecl(String name) {
        this.name = name;
    }
}
