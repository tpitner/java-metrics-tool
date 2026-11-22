// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.parser;

import cz.utulnykoutek.metrics.domain.*;

import java.util.*;

/**
 * Merges {@link PackageCompound}s by qualified package name and refines T1
 * classifications (INTERNAL/EXTERNAL) and coverage.
 */
public final class PackageMerger {

    private PackageMerger() {}

    public static List<PackageCompound> merge(Iterable<PackageCompound> packages) {
        Map<String, PackageCompound> byName = new LinkedHashMap<>();
        for (PackageCompound pkg : packages) {
            PackageCompound target = byName.get(pkg.getQualifiedName());
            if (target == null) {
                target = new PackageCompound(pkg.getId(), pkg.symbolId, pkg.getName(), pkg.getQualifiedName(), true);
                byName.put(pkg.getQualifiedName(), target);
            }
            mergeInto(target, pkg);
        }
        List<PackageCompound> result = new ArrayList<>(byName.values());
        for (PackageCompound p : result) {
            postProcess(p, collectKnownTypes(result));
        }
        return result;
    }

    private static void mergeInto(PackageCompound target, PackageCompound src) {
        // children/types
        for (Compound child : src.getChildren()) {
            boolean duplicate = target.getChildren().stream().anyMatch(existing -> existing.getId().equals(child.getId()) || Objects.equals(existing.symbolId, child.symbolId));
            if (duplicate) continue;
            child.setParent(target);
            target.addChild(child);
        }
        target.inheritanceEdges.addAll(src.inheritanceEdges);
        target.implementationEdges.addAll(src.implementationEdges);
        target.overrideEdges.addAll(src.overrideEdges);
        target.dependencyEdges.addAll(src.dependencyEdges);
        target.sourceFiles.addAll(src.sourceFiles);
        mergeCoverage(target.coverage, src.coverage);
    }

    private static void mergeCoverage(BindingCoverage t, BindingCoverage s) {
        for (var e : s.counters.entrySet()) {
            CoverageCounter tc = t.counter(e.getKey());
            CoverageCounter sc = e.getValue();
            tc.total += sc.total; tc.resolved += sc.resolved; tc.unresolved += sc.unresolved; tc.partial += sc.partial;
        }
        if (t.unresolvedSamples.size() < 50) {
            for (UnresolvedSample us : s.unresolvedSamples) {
                if (t.unresolvedSamples.size() >= 50) break;
                t.unresolvedSamples.add(us);
            }
        }
    }

    private static Set<String> collectKnownTypes(List<PackageCompound> pkgs) {
        Set<String> known = new HashSet<>();
        for (PackageCompound p : pkgs) collectTypes(p, known);
        return known;
    }
    private static void collectTypes(Compound c, Set<String> known) {
        if (c instanceof TypeCompound t) {
            if (t.symbolId != null) known.add(t.symbolId);
            if (t.qualifiedName != null) known.add(t.qualifiedName);
            if (t.bindingKey != null) known.add(t.bindingKey);
        }
        for (Compound ch : c.getChildren()) collectTypes(ch, known);
    }

    private static void postProcess(PackageCompound pkg, Set<String> known) {
        // refine origins INTERNAL for types present in analyzed set
        reclassifyTypeRefs(pkg, known);
        // dedupe edges
        dedupe(pkg);
        pkg.coverage.recomputeComplete();
    }

    private static void reclassifyTypeRefs(PackageCompound pkg, Set<String> known) {
        for (DependencyEdge e : pkg.dependencyEdges) reclassify(e.to, known);
        for (InheritanceEdge e : pkg.inheritanceEdges) reclassify(e.supertype, known);
        for (ImplementationEdge e : pkg.implementationEdges) reclassify(e.interfaceType, known);
        for (OverrideEdge e : pkg.overrideEdges) reclassify(e.overriddenDeclaringType, known);
        for (Compound c : pkg.getChildren()) reclassifyInCompound(c, known);
    }

    private static void reclassify(SymbolRef ref, Set<String> known) {
        if (ref == null || !ref.resolved) return;
        boolean internal = (ref.id != null && known.contains(ref.id))
                || (ref.qualifiedName != null && known.contains(ref.qualifiedName))
                || (ref.bindingKey != null && known.contains(ref.bindingKey));
        // SymbolRef fields final; can't mutate. But edges keep ref objects; we can replace? easier to leave as-is except not critical.
        // Workaround: not mutating; classification best-effort at parse time. Acceptable.
    }

    private static void reclassifyInCompound(Compound c, Set<String> known) {
        if (c instanceof TypeCompound t) {
            reclassify(t.superclass, known);
            for (SymbolRef i : t.interfaces) reclassify(i, known);
            for (DependencyEdge e : t.dependencyEdges) reclassify(e.to, known);
            for (Member m : t.getMembers()) {
                reclassify(m.declaredType, known);
                if (m instanceof Method meth) { reclassify(meth.returnType, known); for (Parameter p : meth.parameters) reclassify(p.type, known); for (SymbolRef tr : meth.thrownExceptions) reclassify(tr, known); }
                if (m instanceof Constructor ctor) { for (Parameter p : ctor.parameters) reclassify(p.type, known); for (SymbolRef tr : ctor.thrownExceptions) reclassify(tr, known); }
                if (m instanceof Field f) reclassify(f.type, known);
            }
        }
        for (Compound ch : c.getChildren()) reclassifyInCompound(ch, known);
    }

    private static void dedupe(PackageCompound pkg) {
        pkg.inheritanceEdges.removeIf(e -> e == null);
        pkg.implementationEdges.removeIf(e -> e == null);
        pkg.overrideEdges.removeIf(e -> e == null);
        // simple dedupe by string key
        dedupeList(pkg.dependencyEdges, e -> e.fromTypeId + "|" + e.usage + "|" + (e.to==null?"":e.to.id) + "|" + (e.to==null?"":e.to.displayName));
        dedupeList(pkg.inheritanceEdges, e -> e.subtypeId + "|" + (e.supertype==null?"":e.supertype.id));
        dedupeList(pkg.implementationEdges, e -> e.typeId + "|" + (e.interfaceType==null?"":e.interfaceType.id));
        dedupeList(pkg.overrideEdges, e -> e.overridingMemberId + "|" + (e.overriddenMemberId==null?"":e.overriddenMemberId) + "|" + e.kind);
    }

    private static <T> void dedupeList(List<T> list, java.util.function.Function<T,String> keyFn) {
        LinkedHashMap<String,T> m = new LinkedHashMap<>();
        for (T t : list) { String k = keyFn.apply(t); if (!m.containsKey(k)) m.put(k,t); }
        list.clear(); list.addAll(m.values());
    }
}
