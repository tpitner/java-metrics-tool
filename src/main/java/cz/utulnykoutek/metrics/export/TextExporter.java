// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.export;

import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.CompoundKind;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import java.util.List;

/**
 * Renders a {@link PackageCompound} tree as the text format specified by the MVP.
 */
public final class TextExporter {
    private TextExporter() {}

    public static String render(PackageCompound pkg, ProviderResult javaMetrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("Package: ").append(pkg.getQualifiedName()).append("\n");
        List<Compound> children = pkg.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) sb.append("\n");
            renderType(children.get(i), 0, sb, javaMetrics);
        }
        return sb.toString();
    }

    private static void renderType(Compound c, int indent, StringBuilder sb, ProviderResult javaMetrics) {
        String pad = "  ".repeat(indent);
        DeclaredMemberCounts counts = DeclaredMemberCounts.from(c, javaMetrics);
        sb.append(pad).append(c.getName()).append(" [").append(label(c.getKind())).append("]\n");
        if (!counts.isEmpty()) {
            sb.append(pad).append("  Declared: methods=").append(value(counts.methods()))
                    .append(", constructors=").append(value(counts.constructors()))
                    .append(", fields=").append(value(counts.fields())).append("\n");
        }

        List<Compound> children = c.getChildren();
        for (int i = 0; i < children.size(); i++) {
            sb.append("\n");
            renderType(children.get(i), indent + 1, sb, javaMetrics);
        }
    }

    private static String value(java.util.OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "n/a";
    }

    private static String label(CompoundKind kind) {
        return switch (kind) {
            case CLASS -> "class";
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case RECORD -> "record";
            case ANNOTATION -> "annotation";
            case PACKAGE -> "package";
        };
    }
}
