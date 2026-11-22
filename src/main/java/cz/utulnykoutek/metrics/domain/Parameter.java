// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

/**
 * Method/constructor parameter.
 */
public class Parameter {
    public final String name;
    public final SymbolRef type;
    public final boolean varargs;
    public final int index;

    public Parameter(String name, SymbolRef type, boolean varargs, int index) {
        this.name = name;
        this.type = type;
        this.varargs = varargs;
        this.index = index;
    }

    /** Compatibility helper for exporters expecting text. */
    public String typeName() {
        return type == null ? "" : type.displayName;
    }
}