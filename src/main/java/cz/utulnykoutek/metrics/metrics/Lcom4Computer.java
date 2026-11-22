// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.domain.*;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.*;

import java.math.BigDecimal;
import java.util.*;

/** Computes the primary Hitz-Montazeri-style LCOM4 variant. */
public final class Lcom4Computer implements BuiltInMetricComputer {
    private static final Set<BuiltInMetricDefinition> DEFINITIONS =
            Set.of(BuiltInMetricDefinition.LCOM4);

    @Override public Set<BuiltInMetricDefinition> definitions() {
        return DEFINITIONS;
    }

    @Override
    public List<MetricMeasurement> compute(AnalysisContext context, String providerRunId) {
        List<MetricMeasurement> result = new ArrayList<>();
        for (PackageCompound pkg : context.packages()) {
            for (Compound child : pkg.getChildren()) collect(child, providerRunId, result);
        }
        return List.copyOf(result);
    }

    private static void collect(
            Compound compound,
            String providerRunId,
            List<MetricMeasurement> result) {
        if (compound instanceof TypeCompound type) result.add(measure(type, providerRunId));
        compound.getChildren().forEach(child -> collect(child, providerRunId, result));
    }

    private static MetricMeasurement measure(TypeCompound type, String providerRunId) {
        SubjectRef subject = new SubjectRef(MetricScope.TYPE, type.symbolId);
        if (type.getKind() == CompoundKind.INTERFACE || type.getKind() == CompoundKind.ANNOTATION) {
            return measurement(subject, providerRunId,
                    new NotApplicableResult(ReasonCode.INTERFACE_OR_ANNOTATION), Map.of());
        }
        TypeCohesionEvidence evidence = type.cohesionEvidence;
        if (evidence == null) {
            return measurement(subject, providerRunId,
                    new UnavailableResult(Set.of(ReasonCode.COHESION_EVIDENCE_UNAVAILABLE)), Map.of());
        }

        List<MethodCohesionEvidence> eligible = evidence.eligibleMethods();
        long accessorCount = evidence.methods().stream()
                .filter(method -> method.exclusions().contains(CohesionMethodExclusion.TRIVIAL_ACCESSOR))
                .count();
        Map<String, String> baseAttributes = new LinkedHashMap<>();
        baseAttributes.put("eligibleMethodCount", Integer.toString(eligible.size()));
        baseAttributes.put("declaredInstanceFieldCount",
                Integer.toString(evidence.declaredInstanceFields().size()));
        baseAttributes.put("excludedAccessorCount", Long.toString(accessorCount));

        if (eligible.isEmpty()) {
            ReasonCode reason = notApplicableReason(evidence);
            return measurement(subject, providerRunId,
                    new NotApplicableResult(reason), baseAttributes);
        }

        GraphResult graph = graph(eligible, evidence.methods());
        baseAttributes.put("fieldEdgeCount", Integer.toString(graph.fieldEdges));
        baseAttributes.put("callEdgeCount", Integer.toString(graph.callEdges));
        baseAttributes.put("unresolvedFieldAccessCount", Integer.toString(graph.unresolvedFields));
        baseAttributes.put("unresolvedIntraTypeCallCount", Integer.toString(graph.unresolvedCalls));
        baseAttributes.put("lowInformation", Boolean.toString(eligible.size() == 1));

        MeasurementResult measured;
        if (graph.unresolvedFields == 0 && graph.unresolvedCalls == 0) {
            measured = NumericResult.exact(BigDecimal.valueOf(graph.components));
        } else {
            EnumSet<ReasonCode> reasons = EnumSet.noneOf(ReasonCode.class);
            if (graph.unresolvedFields > 0) reasons.add(ReasonCode.UNRESOLVED_FIELD_REFERENCE);
            if (graph.unresolvedCalls > 0) reasons.add(ReasonCode.UNRESOLVED_METHOD_CALL);
            measured = NumericResult.atMost(
                    BigDecimal.valueOf(graph.components), reasons, List.of());
        }
        return measurement(subject, providerRunId, measured, baseAttributes);
    }

    private static ReasonCode notApplicableReason(TypeCohesionEvidence evidence) {
        if (!evidence.methods().isEmpty() && evidence.methods().stream().allMatch(method ->
                method.exclusions().contains(CohesionMethodExclusion.STATIC_METHOD))) {
            return ReasonCode.STATIC_UTILITY_TYPE;
        }
        if (!evidence.methods().isEmpty() && evidence.methods().stream().allMatch(method ->
                method.exclusions().contains(CohesionMethodExclusion.TRIVIAL_ACCESSOR))) {
            return ReasonCode.ACCESSORS_ONLY;
        }
        return ReasonCode.NO_ELIGIBLE_MEMBERS;
    }

    private static GraphResult graph(
            List<MethodCohesionEvidence> eligible,
            List<MethodCohesionEvidence> allEvidence) {
        Map<Method, MethodCohesionEvidence> byMethod = new IdentityHashMap<>();
        allEvidence.forEach(method -> byMethod.put(method.method(), method));
        Map<Method, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < eligible.size(); i++) index.put(eligible.get(i).method(), i);

        List<Set<Field>> effectiveFields = new ArrayList<>();
        int unresolvedFields = 0;
        int unresolvedCalls = 0;
        for (MethodCohesionEvidence method : eligible) {
            Set<Field> fields = new LinkedHashSet<>(method.accessedInstanceFields());
            for (Method called : method.calledDeclaredMethods()) {
                MethodCohesionEvidence target = byMethod.get(called);
                if (target != null && target.exclusions().contains(
                        CohesionMethodExclusion.TRIVIAL_ACCESSOR)) {
                    fields.addAll(target.accessedInstanceFields());
                }
            }
            effectiveFields.add(fields);
            unresolvedFields += method.unresolvedFieldAccessCount();
            unresolvedCalls += method.unresolvedIntraTypeCallCount();
        }

        UnionFind union = new UnionFind(eligible.size());
        int fieldEdges = 0;
        for (int left = 0; left < eligible.size(); left++) {
            for (int right = left + 1; right < eligible.size(); right++) {
                if (!Collections.disjoint(effectiveFields.get(left), effectiveFields.get(right))) {
                    fieldEdges++;
                    union.join(left, right);
                }
            }
        }

        Set<Long> callPairs = new HashSet<>();
        for (int source = 0; source < eligible.size(); source++) {
            for (Method called : eligible.get(source).calledDeclaredMethods()) {
                Integer target = index.get(called);
                if (target == null || target == source) continue;
                int low = Math.min(source, target);
                int high = Math.max(source, target);
                long pair = ((long) low << 32) | (high & 0xffffffffL);
                if (callPairs.add(pair)) union.join(low, high);
            }
        }
        return new GraphResult(union.components(), fieldEdges, callPairs.size(),
                unresolvedFields, unresolvedCalls);
    }

    private static MetricMeasurement measurement(
            SubjectRef subject,
            String providerRunId,
            MeasurementResult result,
            Map<String, String> attributes) {
        return new MetricMeasurement(BuiltInMetricDefinition.LCOM4.id(), subject,
                providerRunId, result, attributes);
    }

    private record GraphResult(
            int components,
            int fieldEdges,
            int callEdges,
            int unresolvedFields,
            int unresolvedCalls) {}

    private static final class UnionFind {
        private final int[] parent;
        private int components;

        private UnionFind(int size) {
            parent = new int[size];
            for (int i = 0; i < size; i++) parent[i] = i;
            components = size;
        }

        private int root(int value) {
            int current = value;
            while (parent[current] != current) {
                parent[current] = parent[parent[current]];
                current = parent[current];
            }
            return current;
        }

        private void join(int left, int right) {
            int leftRoot = root(left);
            int rightRoot = root(right);
            if (leftRoot == rightRoot) return;
            parent[rightRoot] = leftRoot;
            components--;
        }

        private int components() {
            return components;
        }
    }
}
