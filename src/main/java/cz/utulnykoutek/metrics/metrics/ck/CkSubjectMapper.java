// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.ck;

import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.Constructor;
import cz.utulnykoutek.metrics.domain.Member;
import cz.utulnykoutek.metrics.domain.Method;
import cz.utulnykoutek.metrics.domain.Parameter;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps CK class and method identities to canonical java-metrics subjects. */
public final class CkSubjectMapper {
    private static final Pattern METHOD_ID = Pattern.compile("^(.+?)/(\\d+)(?:\\[(.*)])?$");

    private final Map<String, List<TypeCompound>> types = new HashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public CkSubjectMapper(AnalysisContext context) {
        for (Compound pkg : context.packages()) {
            for (Compound child : pkg.getChildren()) index(child);
        }
    }

    public Optional<SubjectRef> type(String ckClassName) {
        Optional<TypeCompound> type = resolveType(ckClassName);
        if (type.isEmpty()) return Optional.empty();
        TypeCompound resolved = type.orElseThrow();
        return Optional.of(new SubjectRef(MetricScope.TYPE, subjectId(resolved)));
    }

    public Optional<SubjectRef> method(String ckClassName, String ckMethod, boolean constructor) {
        Optional<TypeCompound> ownerResult = resolveType(ckClassName);
        if (ownerResult.isEmpty()) return Optional.empty();
        TypeCompound owner = ownerResult.orElseThrow();
        Matcher matcher = METHOD_ID.matcher(ckMethod);
        if (!matcher.matches()) {
            diagnostics.add("CK method identity has an unsupported form: " + ckClassName + "#" + ckMethod);
            return Optional.empty();
        }
        String name = matcher.group(1);
        int arity = Integer.parseInt(matcher.group(2));
        List<String> parameterTypes = matcher.group(3) == null || matcher.group(3).isBlank()
                ? List.of()
                : List.of(matcher.group(3).split(",", -1));

        List<Member> candidates = owner.getMembers().stream()
                .filter(member -> constructor ? member instanceof Constructor : member instanceof Method)
                .filter(member -> member.name.equals(name))
                .filter(member -> parameters(member).size() == arity)
                .toList();
        if (candidates.size() > 1 && parameterTypes.size() == arity) {
            List<Member> exact = candidates.stream()
                    .filter(member -> parameterTypesMatch(parameters(member), parameterTypes))
                    .toList();
            if (!exact.isEmpty()) candidates = exact;
        }
        if (candidates.size() != 1) {
            diagnostics.add("Cannot map CK method uniquely: " + ckClassName + "#" + ckMethod
                    + " (candidates=" + candidates.size() + ")");
            return Optional.empty();
        }
        Member member = candidates.getFirst();
        return Optional.of(new SubjectRef(
                constructor ? MetricScope.CONSTRUCTOR : MetricScope.METHOD,
                member.id));
    }

    public List<String> diagnostics() {
        return List.copyOf(diagnostics);
    }

    private Optional<TypeCompound> resolveType(String ckClassName) {
        List<TypeCompound> candidates = types.getOrDefault(normalizeClass(ckClassName), List.of());
        if (candidates.size() != 1) {
            diagnostics.add("Cannot map CK class uniquely: " + ckClassName
                    + " (candidates=" + candidates.size() + ")");
            return Optional.empty();
        }
        return Optional.of(candidates.getFirst());
    }

    private void index(Compound compound) {
        if (compound instanceof TypeCompound type && type.qualifiedName != null) {
            types.computeIfAbsent(normalizeClass(type.qualifiedName), ignored -> new ArrayList<>()).add(type);
        }
        for (Compound child : compound.getChildren()) index(child);
    }

    private static String subjectId(TypeCompound type) {
        return type.symbolId == null ? type.id : type.symbolId;
    }

    private static String normalizeClass(String value) {
        return value == null ? "" : value.trim().replace('$', '.');
    }

    private static List<Parameter> parameters(Member member) {
        if (member instanceof Method method) return method.parameters;
        if (member instanceof Constructor constructor) return constructor.parameters;
        return List.of();
    }

    private static boolean parameterTypesMatch(List<Parameter> actual, List<String> expected) {
        for (int i = 0; i < actual.size(); i++) {
            Parameter parameter = actual.get(i);
            String actualType = parameter.type == null
                    ? parameter.typeName()
                    : parameter.type.qualifiedName == null
                            ? parameter.type.displayName
                            : parameter.type.qualifiedName;
            if (!normalizeType(actualType, parameter.varargs).equals(normalizeType(expected.get(i), false))) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeType(String value, boolean varargs) {
        if (value == null) return "";
        String normalized = value.replace(" ", "").replace('$', '.');
        normalized = eraseGenerics(normalized);
        if (varargs && normalized.endsWith("...")) normalized = normalized.substring(0, normalized.length() - 3) + "[]";
        if (varargs && !normalized.endsWith("[]")) normalized += "[]";
        return normalized;
    }

    private static String eraseGenerics(String value) {
        StringBuilder result = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '<') depth++;
            else if (character == '>') depth--;
            else if (depth == 0) result.append(character);
        }
        return result.toString();
    }
}
