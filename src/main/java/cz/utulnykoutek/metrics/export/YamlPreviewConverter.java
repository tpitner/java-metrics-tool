// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.export;

import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.CompoundKind;
import cz.utulnykoutek.metrics.domain.Member;
import cz.utulnykoutek.metrics.domain.Modifier;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.metrics.ProviderResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Colored ANSI tree preview of an analyzed package.
 */
public final class YamlPreviewConverter {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String GRAY = "\u001B[90m";
    private static final String RED = "\u001B[31m";

    private YamlPreviewConverter() {}

    public static String render(PackageCompound pkg, ProviderResult javaMetrics) {
        StringBuilder sb = new StringBuilder();
        int typeCount = countTypes(pkg);
        sb.append(BOLD).append(CYAN).append("Package: ").append(pkg.getQualifiedName()).append(RESET).append("\n");
        sb.append(GRAY).append("  classes=").append(typeCount)
                .append(RESET).append("\n");
        for (Compound c : pkg.getChildren()) {
            renderType(c, 1, sb, javaMetrics);
        }
        return sb.toString();
    }

    public static void print(PackageCompound pkg, ProviderResult javaMetrics) {
        System.out.print(render(pkg, javaMetrics));
    }

    private static void renderType(Compound c, int level, StringBuilder sb, ProviderResult javaMetrics) {
        String indent = "  ".repeat(level);
        sb.append(indent).append(BOLD).append(MAGENTA).append(c.getName()).append(RESET);
        if (c instanceof TypeCompound tc && !tc.dependencies.isEmpty()) {
            List<String> deps = new ArrayList<>();
            for (List<String> v : tc.dependencies.values()) deps.addAll(v);
            sb.append(GRAY).append("  depends on ").append(deps).append(RESET);
        }
        sb.append(GRAY).append(" [").append(kind(c.getKind())).append("]").append(RESET).append("\n");
        DeclaredMemberCounts counts = DeclaredMemberCounts.from(c, javaMetrics);
        if (!counts.isEmpty()) {
            sb.append(indent).append(GRAY).append("  declared: methods=").append(value(counts.methods()))
                    .append(", constructors=").append(value(counts.constructors()))
                    .append(", fields=").append(value(counts.fields())).append(RESET).append("\n");
        }

        List<Member> members = new ArrayList<>(c.getMembers());
        members.sort(Comparator.comparingInt(YamlPreviewConverter::visibilityRank).thenComparing(m -> m.name));
        for (Member m : members) {
            String color = colorFor(m.modifiers);
            sb.append(indent).append("  ").append(color).append(memberLabel(m)).append(RESET).append("\n");
        }

        for (Compound child : c.getChildren()) {
            renderType(child, level + 1, sb, javaMetrics);
        }
    }

    private static String memberLabel(Member m) {
        String mods = m.modifiers.isEmpty() ? "" : m.modifiers.toString().toLowerCase().replace("[", "").replace("]", "") + " ";
        String kind = m instanceof cz.utulnykoutek.metrics.domain.Method ? "method"
                : m instanceof cz.utulnykoutek.metrics.domain.Constructor ? "constructor"
                : m instanceof cz.utulnykoutek.metrics.domain.Field ? "field"
                : m instanceof cz.utulnykoutek.metrics.domain.EnumConstant ? "enum-constant" : "member";
        String type = (m.javaType == null || m.javaType.isBlank()) ? "" : " : " + m.javaType;
        if (m instanceof cz.utulnykoutek.metrics.domain.Method meth) {
            type = meth.returnType == null ? "" : " : " + meth.returnType.displayName;
        }
        return mods + kind + " " + m.name + type;
    }

    private static int visibilityRank(Member m) {
        if (m.modifiers.contains(Modifier.PUBLIC)) return 0;
        if (m.modifiers.contains(Modifier.PROTECTED)) return 1;
        if (m.modifiers.contains(Modifier.PRIVATE)) return 3;
        return 2;
    }

    private static String colorFor(java.util.EnumSet<Modifier> mods) {
        if (mods.contains(Modifier.PUBLIC)) return GREEN;
        if (mods.contains(Modifier.PROTECTED)) return YELLOW;
        if (mods.contains(Modifier.PRIVATE)) return RED;
        return BLUE;
    }

    private static String kind(CompoundKind k) {
        return switch (k) {
            case CLASS -> "class";
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case RECORD -> "record";
            case ANNOTATION -> "annotation";
            case PACKAGE -> "package";
        };
    }

    private static int countTypes(Compound c) {
        int n = 0;
        for (Compound ch : c.getChildren()) {
            n += 1 + countTypes(ch);
        }
        return n;
    }

    private static String value(java.util.OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "n/a";
    }

}
