// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Common base class for all container-like entities in the model: packages and
 * declared types. A compound may contain child compounds (nested types) and
 * members (methods, constructors, fields).
 */
public abstract class Compound {

    /** Stable identifier of this compound (legacy/primary). */
    public final String id;
    /** Canonical symbol identifier. */
    public String symbolId;
    /** Kind of this compound (package, class, interface, ...). */
    public final CompoundKind kind;
    /** Simple name of this compound. */
    public final String name;
    /** Fully qualified name of this compound. */
    public final String qualifiedName;
    /** Whether identity/bindings are resolved for this node. */
    public boolean resolved;
    /** Exact source range for declared types; packages have no single range. */
    public SourceSpan sourceSpan;
    /** Parent compound, or {@code null} for a top-level package. */
    private Compound parent;

    /** Child compounds (nested types, incl. local/anonymous). */
    private final List<Compound> children = new ArrayList<>();
    /** Directly declared members. */
    private final List<Member> members = new ArrayList<>();

    protected Compound(String id, String symbolId, CompoundKind kind, String name, String qualifiedName,
                       Compound parent, boolean resolved) {
        this.id = id;
        this.symbolId = symbolId == null ? id : symbolId;
        this.kind = kind;
        this.name = name;
        this.qualifiedName = qualifiedName;
        this.parent = parent;
        this.resolved = resolved;
    }

    /** Compatibility constructor. */
    protected Compound(String id, CompoundKind kind, String name, String qualifiedName, Compound parent) {
        this(id, id, kind, name, qualifiedName, parent, false);
    }

    /** @return child compounds (nested types). */
    public List<Compound> getChildren() { return children; }
    /** @return directly declared members. */
    public List<Member> getMembers() { return members; }

    /** @return stable identifier. */
    public String getId() { return id; }
    /** @return kind of this compound. */
    public CompoundKind getKind() { return kind; }
    /** @return simple name. */
    public String getName() { return name; }
    /** @return fully qualified name. */
    public String getQualifiedName() { return qualifiedName; }
    /** @return parent compound, or {@code null}. */
    public Compound getParent() { return parent; }

    /** Sets the parent compound (used when merging packages). */
    public void setParent(Compound parent) { this.parent = parent; }

    /** Adds a child compound. */
    public void addChild(Compound c) { children.add(c); }
    /** Adds a member. */
    public void addMember(Member m) { members.add(m); }

}
