// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.export;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.*;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.measurement.IntervalResult;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.UnavailableResult;

import java.util.*;

/**
 * Renders T1 analysis output as JSON.
 */
public final class JsonExporter {
    private JsonExporter() {}

    public static String render(
            PackageCompound pkg,
            BaselineMetadata metadata,
            ProviderResult javaMetrics) {
        return render(pkg, metadata, javaMetrics, List.of(javaMetrics));
    }

    public static String render(
            PackageCompound pkg,
            BaselineMetadata metadata,
            ProviderResult javaMetrics,
            List<ProviderResult> providerResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        if (metadata != null) {
            sb.append("  \"baseline\": {\n");
            sb.append("    \"toolVersion\": ").append(q(metadata.toolVersion)).append(",\n");
            sb.append("    \"jdtCoreVersion\": ").append(q(metadata.jdtCoreVersion)).append(",\n");
            sb.append("    \"jdkVersion\": ").append(q(metadata.jdkVersion)).append(",\n");
            sb.append("    \"jdkVendor\": ").append(q(metadata.jdkVendor)).append(",\n");
            sb.append("    \"parser\": ").append(object(metadata.parser, 2)).append(",\n");
            sb.append("    \"configuration\": ").append(object(metadata.configuration, 2)).append("\n");
            sb.append("  },\n");
        }
        sb.append("  \"package\": {\n");
        sb.append("    \"symbolId\": ").append(q(pkg.symbolId)).append(",\n");
        sb.append("    \"qualifiedName\": ").append(q(pkg.getQualifiedName())).append(",\n");
        sb.append("    \"coverage\": ").append(coverage(pkg.coverage, 2)).append("\n");
        sb.append("  },\n");
        sb.append("  \"types\": [\n");
        List<Compound> children = pkg.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) sb.append(",\n");
            renderType(children.get(i), 2, sb, javaMetrics);
        }
        sb.append("\n  ],\n");
        sb.append("  \"relations\": {\n");
        sb.append("    \"inheritance\": ").append(inheritanceEdges(pkg.inheritanceEdges, 2)).append(",\n");
        sb.append("    \"implementations\": ").append(implementationEdges(pkg.implementationEdges, 2)).append(",\n");
        sb.append("    \"overrides\": ").append(overrideEdges(pkg.overrideEdges, 2)).append("\n");
        sb.append("  },\n");
        sb.append("  \"dependencyEdges\": ").append(dependencyEdges(pkg.dependencyEdges, 1)).append(",\n");
        sb.append("  \"coverage\": ").append(coverage(pkg.coverage, 1)).append(",\n");
        sb.append("  \"providerRuns\": ").append(providerRuns(providerResults)).append(",\n");
        sb.append("  \"measurements\": ").append(measurements(providerResults)).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String providerRuns(List<ProviderResult> results) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append(",");
            ProviderResult result = results.get(i);
            sb.append("{\"providerId\":").append(q(result.run().providerId()))
                    .append(",\"providerRunId\":").append(q(result.run().providerRunId()))
                    .append(",\"version\":").append(q(result.run().version()))
                    .append(",\"configuration\":").append(object(result.run().configuration(), 1))
                    .append(",\"diagnostics\":").append(stringArray(result.diagnostics()))
                    .append("}");
        }
        return sb.append("]").toString();
    }

    private static String measurements(List<ProviderResult> results) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ProviderResult provider : results) {
            for (MetricMeasurement measurement : provider.measurements().all()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"metricId\":").append(q(measurement.metricId().serializedName()))
                        .append(",\"scope\":").append(q(measurement.subject().scope().serializedName()))
                        .append(",\"subjectId\":").append(q(measurement.subject().id()))
                        .append(",\"providerRunId\":").append(q(measurement.providerRunId()))
                        .append(",\"status\":").append(q(measurement.status().name()))
                        .append(",\"result\":").append(measurementResult(measurement))
                        .append(",\"attributes\":").append(object(measurement.attributes(), 1))
                        .append("}");
            }
        }
        return sb.append("]").toString();
    }

    private static String measurementResult(MetricMeasurement measurement) {
        return switch (measurement.result()) {
            case NumericResult numeric -> "{\"kind\":\"numeric\",\"value\":"
                    + numeric.value().toPlainString() + ",\"relation\":" + q(numeric.relation().name())
                    + ",\"reasons\":" + enumNames(numeric.reasons())
                    + ",\"coverage\":" + measurementCoverage(numeric.coverage()) + "}";
            case IntervalResult interval -> "{\"kind\":\"interval\",\"lower\":"
                    + interval.lower().toPlainString() + ",\"upper\":"
                    + interval.upper().toPlainString() + ",\"reasons\":"
                    + enumNames(interval.reasons()) + ",\"coverage\":"
                    + measurementCoverage(interval.coverage()) + "}";
            case UnavailableResult unavailable -> "{\"kind\":\"unavailable\",\"reasons\":"
                    + enumNames(unavailable.reasons()) + "}";
            case NotApplicableResult notApplicable -> "{\"kind\":\"not-applicable\",\"reason\":"
                    + q(notApplicable.reason().name()) + "}";
        };
    }

    private static String measurementCoverage(
            List<cz.utulnykoutek.metrics.metrics.measurement.CoverageEvidence> coverage) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < coverage.size(); i++) {
            if (i > 0) sb.append(",");
            var evidence = coverage.get(i);
            sb.append("{\"dimension\":").append(q(evidence.dimension().name()))
                    .append(",\"resolved\":").append(evidence.resolved())
                    .append(",\"total\":").append(evidence.total()).append("}");
        }
        return sb.append("]").toString();
    }

    private static String enumNames(Collection<? extends Enum<?>> values) {
        return stringArray(values.stream().map(Enum::name).sorted().toList());
    }

    private static void renderType(
            Compound c,
            int indent,
            StringBuilder sb,
            ProviderResult javaMetrics) {
        String pad = "  ".repeat(indent);
        String in = "  ".repeat(indent + 1);
        sb.append(pad).append("{\n");
        sb.append(in).append("\"symbolId\": ").append(q(c.symbolId)).append(",\n");
        sb.append(in).append("\"id\": ").append(q(c.getId())).append(",\n");
        sb.append(in).append("\"kind\": ").append(q(kind(c.getKind()))).append(",\n");
        sb.append(in).append("\"name\": ").append(q(c.getName())).append(",\n");
        sb.append(in).append("\"qualifiedName\": ").append(q(c.getQualifiedName())).append(",\n");
        sb.append(in).append("\"resolved\": ").append(c.resolved).append(",\n");
        if (c instanceof TypeCompound tc) {
            sb.append(in).append("\"bindingKey\": ").append(q(tc.bindingKey)).append(",\n");
            sb.append(in).append("\"nesting\": ").append(q(tc.nesting.name())).append(",\n");
            sb.append(in).append("\"declaredInMemberId\": ").append(q(tc.declaredInMemberId)).append(",\n");
            sb.append(in).append("\"modifiers\": ").append(modifiers(tc.modifiers)).append(",\n");
            sb.append(in).append("\"typeParameters\": ").append(typeParams(tc.typeParameters)).append(",\n");
            sb.append(in).append("\"superclass\": ").append(symbolRef(tc.superclass)).append(",\n");
            sb.append(in).append("\"interfaces\": ").append(symbolRefArray(tc.interfaces)).append(",\n");
            sb.append(in).append("\"dependencyEdges\": ").append(dependencyEdges(tc.dependencyEdges, indent + 1)).append(",\n");
            sb.append(in).append("\"dependencySummary\": ").append(dependencies(tc.dependencies)).append(",\n");
            sb.append(in).append("\"sourceSpan\": ").append(sourceSpan(tc.sourceSpan)).append(",\n");
            sb.append(in).append("\"initializers\": ").append(initializers(tc.initializers)).append(",\n");
            sb.append(in).append("\"lambdas\": ").append(lambdas(tc.lambdas)).append(",\n");
        }
        DeclaredMemberCounts counts = DeclaredMemberCounts.from(c, javaMetrics);
        sb.append(in).append("\"declaredMemberCounts\": ")
                .append(declaredMemberCounts(counts)).append(",\n");
        sb.append(in).append("\"members\": ").append(members(c.getMembers(), indent + 1)).append(",\n");
        sb.append(in).append("\"children\": [\n");
        List<Compound> children = c.getChildren();
        for (int i = 0; i < children.size(); i++) { if (i > 0) sb.append(",\n"); renderType(children.get(i), indent + 2, sb, javaMetrics); }
        sb.append("\n").append(in).append("]\n");
        sb.append(pad).append("}");
    }

    private static String members(List<Member> members, int indent) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < members.size(); i++) { if (i>0) sb.append(",\n").append("  ".repeat(indent+1)); else sb.append("\n").append("  ".repeat(indent+1)); renderMember(members.get(i), indent+1, sb); }
        if (!members.isEmpty()) sb.append("\n").append("  ".repeat(indent));
        sb.append("]"); return sb.toString();
    }

    private static void renderMember(Member m, int indent, StringBuilder sb) {
        String in = "  ".repeat(indent + 1);
        sb.append("{\n");
        sb.append(in).append("\"id\": ").append(q(m.id)).append(",\n");
        sb.append(in).append("\"bindingKey\": ").append(q(m.bindingKey)).append(",\n");
        sb.append(in).append("\"name\": ").append(q(m.name)).append(",\n");
        sb.append(in).append("\"kind\": ").append(q(m.memberKind.name())).append(",\n");
        sb.append(in).append("\"resolved\": ").append(m.resolved).append(",\n");
        sb.append(in).append("\"modifiers\": ").append(modifiers(m.modifiers)).append(",\n");
        sb.append(in).append("\"sourceSpan\": ").append(sourceSpan(m.sourceSpan)).append(",\n");
        if (m instanceof Method meth) {
            sb.append(in).append("\"returnType\": ").append(symbolRef(meth.returnType)).append(",\n");
            sb.append(in).append("\"parameters\": ").append(params(meth.parameters)).append(",\n");
            sb.append(in).append("\"thrownExceptions\": ").append(symbolRefArray(meth.thrownExceptions)).append("\n");
        } else if (m instanceof Constructor ctor) {
            sb.append(in).append("\"parameters\": ").append(params(ctor.parameters)).append(",\n");
            sb.append(in).append("\"thrownExceptions\": ").append(symbolRefArray(ctor.thrownExceptions)).append("\n");
        } else if (m instanceof Field f) {
            sb.append(in).append("\"type\": ").append(symbolRef(f.type)).append("\n");
        } else if (m instanceof EnumConstant ec) {
            sb.append(in).append("\"type\": ").append(symbolRef(ec.declaredType)).append("\n");
        } else {
            sb.append(in).append("\"type\": ").append(symbolRef(m.declaredType)).append("\n");
        }
        sb.append("  ".repeat(indent)).append("}");
    }

    private static String initializers(List<InitializerBlock> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            InitializerBlock value = values.get(i);
            sb.append("{\"id\":").append(q(value.id()))
                    .append(",\"static\":").append(value.isStatic())
                    .append(",\"sourceSpan\":").append(sourceSpan(value.sourceSpan())).append("}");
        }
        return sb.append("]").toString();
    }

    private static String lambdas(List<LambdaExpressionEntity> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            LambdaExpressionEntity value = values.get(i);
            sb.append("{\"id\":").append(q(value.id()))
                    .append(",\"enclosingSubjectId\":").append(q(value.enclosingSubjectId()))
                    .append(",\"sourceSpan\":").append(sourceSpan(value.sourceSpan())).append("}");
        }
        return sb.append("]").toString();
    }

    private static String sourceSpan(SourceSpan span) {
        if (span == null) return "null";
        return "{\"sourceFile\":" + q(span.sourceFile().toString())
                + ",\"startOffset\":" + span.startOffset()
                + ",\"endOffset\":" + span.endOffset() + "}";
    }

    private static String params(List<Parameter> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Parameter p = list.get(i);
            sb.append("{\"name\":").append(q(p.name)).append(",\"type\":").append(symbolRef(p.type)).append(",\"varargs\":").append(p.varargs).append(",\"index\":").append(p.index).append("}");
        }
        sb.append("]"); return sb.toString();
    }

    private static String symbolRef(SymbolRef r) {
        if (r == null) return "null";
        return "{\"id\":" + q(r.id) + ",\"bindingKey\":" + q(r.bindingKey) + ",\"qualifiedName\":" + q(r.qualifiedName)
                + ",\"packageName\":" + q(r.packageName)
                + ",\"displayName\":" + q(r.displayName) + ",\"origin\":" + q(r.origin.name())
                + ",\"resolved\":" + r.resolved + ",\"kindHint\":" + q(r.kindHint.name()) + "}";
    }
    private static String symbolRefArray(List<SymbolRef> list) {
        StringBuilder sb = new StringBuilder("["); for (int i=0;i<list.size();i++){ if(i>0) sb.append(","); sb.append(symbolRef(list.get(i))); } sb.append("]"); return sb.toString();
    }

    private static String typeParams(List<TypeParameterDecl> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<list.size();i++){ if(i>0) sb.append(","); TypeParameterDecl tp=list.get(i); sb.append("{\"name\":").append(q(tp.name)).append(",\"bounds\":").append(symbolRefArray(tp.bounds)).append("}"); }
        sb.append("]"); return sb.toString();
    }

    private static String dependencyEdges(List<DependencyEdge> list, int indent) {
        String pad = "  ".repeat(indent);
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<list.size();i++){ if(i>0) sb.append(","); DependencyEdge e=list.get(i);
            sb.append("{\"fromTypeId\":").append(q(e.fromTypeId)).append(",\"to\":").append(symbolRef(e.to))
              .append(",\"usage\":").append(q(e.usage.name())).append(",\"origin\":").append(q(e.origin.name()))
              .append(",\"resolved\":").append(e.resolved).append("}"); }
        sb.append("]"); return sb.toString();
    }
    private static String inheritanceEdges(List<InheritanceEdge> list, int indent) {
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<list.size();i++){ if(i>0) sb.append(","); InheritanceEdge e=list.get(i);
            sb.append("{\"subtypeId\":").append(q(e.subtypeId)).append(",\"supertype\":").append(symbolRef(e.supertype)).append(",\"resolved\":").append(e.resolved).append("}"); }
        sb.append("]"); return sb.toString();
    }
    private static String implementationEdges(List<ImplementationEdge> list, int indent) {
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<list.size();i++){ if(i>0) sb.append(","); ImplementationEdge e=list.get(i);
            sb.append("{\"typeId\":").append(q(e.typeId)).append(",\"interfaceType\":").append(symbolRef(e.interfaceType)).append(",\"resolved\":").append(e.resolved).append("}"); }
        sb.append("]"); return sb.toString();
    }
    private static String overrideEdges(List<OverrideEdge> list, int indent) {
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<list.size();i++){ if(i>0) sb.append(","); OverrideEdge e=list.get(i);
            sb.append("{\"overridingMemberId\":").append(q(e.overridingMemberId)).append(",\"overriddenMemberId\":").append(q(e.overriddenMemberId))
              .append(",\"overriddenDeclaringType\":").append(symbolRef(e.overriddenDeclaringType)).append(",\"resolved\":").append(e.resolved)
              .append(",\"kind\":").append(q(e.kind)).append("}"); }
        sb.append("]"); return sb.toString();
    }

    private static String coverage(BindingCoverage c, int indent) {
        String in = "  ".repeat(indent + 1);
        StringBuilder sb = new StringBuilder("{\n");
        sb.append(in).append("\"scope\": ").append(q(c.scope)).append(",\n");
        sb.append(in).append("\"scopeId\": ").append(q(c.scopeId)).append(",\n");
        sb.append(in).append("\"complete\": ").append(c.complete).append(",\n");
        sb.append(in).append("\"counters\": {\n");
        int i=0; for (var e : c.counters.entrySet()) { if(i++>0) sb.append(",\n"); CoverageCounter cc=e.getValue();
            sb.append("  ".repeat(indent+2)).append(q(e.getKey())).append(": {\"total\":").append(cc.total).append(",\"resolved\":").append(cc.resolved).append(",\"unresolved\":").append(cc.unresolved).append(",\"partial\":").append(cc.partial).append("}"); }
        if(!c.counters.isEmpty()) sb.append("\n");
        sb.append(in).append("},\n");
        sb.append(in).append("\"unresolvedSamples\": [");
        for (int j=0;j<c.unresolvedSamples.size();j++){ if(j>0) sb.append(","); UnresolvedSample u=c.unresolvedSamples.get(j);
            sb.append("{\"category\":").append(q(u.category)).append(",\"ownerId\":").append(q(u.ownerId)).append(",\"display\":").append(q(u.display)).append(",\"usage\":").append(q(u.usage)).append(",\"reasonHint\":").append(q(u.reasonHint)).append("}"); }
        sb.append("]\n");
        sb.append("  ".repeat(indent)).append("}");
        return sb.toString();
    }

    private static String declaredMemberCounts(DeclaredMemberCounts c) {
        return "{\"methods\":" + optionalInt(c.methods())
                + ",\"constructors\":" + optionalInt(c.constructors())
                + ",\"fields\":" + optionalInt(c.fields()) + "}";
    }
    private static String optionalInt(java.util.OptionalInt value) { return value.isPresent() ? Integer.toString(value.getAsInt()) : "null"; }
    private static String modifiers(EnumSet<Modifier> mods) { StringBuilder sb=new StringBuilder("["); boolean f=true; for(Modifier m:mods){ if(!f) sb.append(","); f=false; sb.append(q(m.name())); } sb.append("]"); return sb.toString(); }
    private static String kind(CompoundKind k){ return switch(k){ case CLASS->"class"; case INTERFACE->"interface"; case ENUM->"enum"; case RECORD->"record"; case ANNOTATION->"annotation"; case PACKAGE->"package"; }; }
    private static String dependencies(Map<String,List<String>> deps){ StringBuilder sb=new StringBuilder("{"); boolean f=true; for(var e:deps.entrySet()){ if(!f) sb.append(","); f=false; sb.append(q(e.getKey())).append(":").append(stringArray(e.getValue())); } sb.append("}"); return sb.toString(); }
    private static String stringArray(List<String> list){ StringBuilder sb=new StringBuilder("["); for(int i=0;i<list.size();i++){ if(i>0) sb.append(","); sb.append(q(list.get(i))); } sb.append("]"); return sb.toString(); }
    private static String object(Map<String, ?> map, int indent){ String pad="  ".repeat(indent); String in="  ".repeat(indent+1); StringBuilder sb=new StringBuilder("{\n"); int i=0; for(var e:map.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){ if(i++>0) sb.append(",\n"); sb.append(in).append(q(e.getKey())).append(": ").append(value(e.getValue(), indent+1)); } sb.append("\n").append(pad).append("}"); return sb.toString(); }
    @SuppressWarnings("unchecked") private static String value(Object v,int indent){ if(v==null) return "null"; if(v instanceof Boolean b) return b.toString(); if(v instanceof Number n) return n.toString(); if(v instanceof String s) return q(s); if(v instanceof List<?> l){ StringBuilder sb=new StringBuilder("["); for(int i=0;i<l.size();i++){ if(i>0) sb.append(","); Object it=l.get(i); sb.append(it==null?"null":q(String.valueOf(it))); } sb.append("]"); return sb.toString(); } if(v instanceof Map<?,?> m) return object((Map<String,?>)m, indent); return q(String.valueOf(v)); }
    private static String q(String s){ if(s==null) return "null"; StringBuilder sb=new StringBuilder("\""); for(char ch:s.toCharArray()){ switch(ch){ case '"'->sb.append("\\\""); case '\\'->sb.append("\\\\"); case '\n'->sb.append("\\n"); case '\r'->sb.append("\\r"); case '\t'->sb.append("\\t"); default->{ if(ch<0x20) sb.append(String.format("\\u%04x",(int)ch)); else sb.append(ch);} } } sb.append("\""); return sb.toString(); }
}
