// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.Constructor;
import cz.utulnykoutek.metrics.domain.InitializerBlock;
import cz.utulnykoutek.metrics.domain.LambdaExpressionEntity;
import cz.utulnykoutek.metrics.domain.Member;
import cz.utulnykoutek.metrics.domain.Method;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.domain.SourceSpan;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TextBlock;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Computes physical source lines and per-line NCLOC directly from source text and JDT ranges. */
public final class SourceLineMetricsComputer implements BuiltInMetricComputer {
    private static final Set<BuiltInMetricDefinition> DEFINITIONS = Set.copyOf(EnumSet.of(
            BuiltInMetricDefinition.PHYSICAL_LOC,
            BuiltInMetricDefinition.NCLOC,
            BuiltInMetricDefinition.PHYSICAL_LOC_EXCLUSIVE,
            BuiltInMetricDefinition.NCLOC_EXCLUSIVE));

    @Override
    public Set<BuiltInMetricDefinition> definitions() {
        return DEFINITIONS;
    }

    @Override
    public List<MetricMeasurement> compute(AnalysisContext context, String providerRunId) {
        return compute(context, providerRunId, DEFINITIONS);
    }

    @Override
    public List<MetricMeasurement> compute(
            AnalysisContext context,
            String providerRunId,
            Set<BuiltInMetricDefinition> requestedDefinitions) {
        Objects.requireNonNull(context, "context");
        if (!DEFINITIONS.containsAll(requestedDefinitions)) {
            throw new IllegalArgumentException("SourceLineMetricsComputer received an unsupported metric");
        }

        Map<Path, SourceFileLines> files = load(context.sourceFiles());
        List<MetricMeasurement> result = new ArrayList<>();

        long projectPhysical = 0;
        long projectNcloc = 0;
        for (Map.Entry<Path, SourceFileLines> entry : files.entrySet()) {
            SourceFileLines lines = entry.getValue();
            long physical = lines.physicalFileLines();
            long ncloc = lines.nclocFileLines();
            projectPhysical += physical;
            projectNcloc += ncloc;
            SubjectRef subject = new SubjectRef(MetricScope.FILE, entry.getKey().toString());
            addPair(result, requestedDefinitions, subject, providerRunId, physical, ncloc);
        }
        addPair(result, requestedDefinitions, new SubjectRef(MetricScope.PROJECT, "project"),
                providerRunId, projectPhysical, projectNcloc);

        for (PackageCompound pkg : context.packages()) {
            long physical = 0;
            long ncloc = 0;
            for (Path sourceFile : pkg.sourceFiles) {
                SourceFileLines lines = files.get(sourceFile.toAbsolutePath().normalize());
                if (lines != null) {
                    physical += lines.physicalFileLines();
                    ncloc += lines.nclocFileLines();
                }
            }
            addPair(result, requestedDefinitions,
                    new SubjectRef(MetricScope.PACKAGE, pkg.symbolId), providerRunId, physical, ncloc);
            for (Compound child : pkg.getChildren()) {
                collectType(child, files, providerRunId, requestedDefinitions, result);
            }
        }
        return List.copyOf(result);
    }

    private static void collectType(
            Compound compound,
            Map<Path, SourceFileLines> files,
            String providerRunId,
            Set<BuiltInMetricDefinition> requestedDefinitions,
            List<MetricMeasurement> result) {
        if (compound instanceof TypeCompound type && type.sourceSpan != null) {
            SourceFileLines lines = files.get(type.sourceSpan.sourceFile());
            if (lines != null) {
                SubjectRef subject = new SubjectRef(MetricScope.TYPE, type.symbolId);
                addPair(result, requestedDefinitions, subject, providerRunId,
                        lines.physical(type.sourceSpan, List.of()),
                        lines.ncloc(type.sourceSpan, List.of()));
                List<SourceSpan> nested = type.getChildren().stream()
                        .map(child -> child.sourceSpan)
                        .filter(Objects::nonNull)
                        .toList();
                addIfRequested(result, requestedDefinitions,
                        BuiltInMetricDefinition.PHYSICAL_LOC_EXCLUSIVE, subject, providerRunId,
                        lines.physical(type.sourceSpan, nested));
                addIfRequested(result, requestedDefinitions,
                        BuiltInMetricDefinition.NCLOC_EXCLUSIVE, subject, providerRunId,
                        lines.ncloc(type.sourceSpan, nested));
            }
            for (Member member : type.getMembers()) {
                if (member.sourceSpan == null) continue;
                SourceFileLines memberLines = files.get(member.sourceSpan.sourceFile());
                if (memberLines == null) continue;
                MetricScope scope = member instanceof Constructor
                        ? MetricScope.CONSTRUCTOR
                        : member instanceof Method ? MetricScope.METHOD : null;
                if (scope != null) {
                    addPair(result, requestedDefinitions, new SubjectRef(scope, member.id), providerRunId,
                            memberLines.physical(member.sourceSpan, List.of()),
                            memberLines.ncloc(member.sourceSpan, List.of()));
                }
            }
            for (InitializerBlock initializer : type.initializers) {
                addPair(result, requestedDefinitions,
                        new SubjectRef(MetricScope.INITIALIZER, initializer.id()), providerRunId,
                        lines.physical(initializer.sourceSpan(), List.of()),
                        lines.ncloc(initializer.sourceSpan(), List.of()));
            }
            for (LambdaExpressionEntity lambda : type.lambdas) {
                addPair(result, requestedDefinitions,
                        new SubjectRef(MetricScope.LAMBDA, lambda.id()), providerRunId,
                        lines.physical(lambda.sourceSpan(), List.of()),
                        lines.ncloc(lambda.sourceSpan(), List.of()));
            }
        }
        for (Compound child : compound.getChildren()) {
            collectType(child, files, providerRunId, requestedDefinitions, result);
        }
    }

    private static Map<Path, SourceFileLines> load(List<Path> sourceFiles) {
        Map<Path, SourceFileLines> result = new HashMap<>();
        for (Path sourceFile : sourceFiles) {
            Path normalized = sourceFile.toAbsolutePath().normalize();
            try {
                result.put(normalized, new SourceFileLines(Files.readString(normalized)));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read source file for LOC metrics: " + normalized, e);
            }
        }
        return Map.copyOf(result);
    }

    private static void addPair(
            List<MetricMeasurement> result,
            Set<BuiltInMetricDefinition> requestedDefinitions,
            SubjectRef subject,
            String providerRunId,
            long physical,
            long ncloc) {
        addIfRequested(result, requestedDefinitions, BuiltInMetricDefinition.PHYSICAL_LOC,
                subject, providerRunId, physical);
        addIfRequested(result, requestedDefinitions, BuiltInMetricDefinition.NCLOC,
                subject, providerRunId, ncloc);
    }

    private static void addIfRequested(
            List<MetricMeasurement> result,
            Set<BuiltInMetricDefinition> requestedDefinitions,
            BuiltInMetricDefinition definition,
            SubjectRef subject,
            String providerRunId,
            long value) {
        if (requestedDefinitions.contains(definition)) {
            result.add(new MetricMeasurement(definition.id(), subject, providerRunId,
                    NumericResult.exact(BigDecimal.valueOf(value))));
        }
    }

    static final class SourceFileLines {
        private final String source;
        private final List<Integer> lineStarts;
        private final boolean[] ignored;
        private final boolean[] forcedToken;

        SourceFileLines(String source) {
            this.source = Objects.requireNonNull(source, "source");
            this.lineStarts = lineStarts(source);
            this.ignored = new boolean[source.length()];
            this.forcedToken = new boolean[source.length()];
            classifyIgnoredRanges();
        }

        long physicalFileLines() {
            return lineStarts.size();
        }

        long nclocFileLines() {
            return countLines(0, source.length(), List.of(), true);
        }

        long physical(SourceSpan span, List<SourceSpan> excluded) {
            return countLines(span.startOffset(), span.endOffset(), excluded, false);
        }

        long ncloc(SourceSpan span, List<SourceSpan> excluded) {
            return countLines(span.startOffset(), span.endOffset(), excluded, true);
        }

        private long countLines(int start, int end, List<SourceSpan> excluded, boolean codeOnly) {
            if (source.isEmpty() || end <= start) return 0;
            int boundedStart = Math.max(0, Math.min(start, source.length()));
            int boundedEnd = Math.max(boundedStart, Math.min(end, source.length()));
            if (boundedEnd == boundedStart) return 0;
            int firstLine = lineOf(boundedStart);
            int lastLine = lineOf(boundedEnd - 1);
            long count = 0;
            for (int line = firstLine; line <= lastLine; line++) {
                int lineStart = Math.max(boundedStart, lineStarts.get(line));
                int lineEnd = Math.min(boundedEnd, lineEnd(line));
                if (codeOnly ? hasCode(line, lineStart, lineEnd, excluded)
                        : hasPhysicalContent(lineStart, lineEnd, excluded)) {
                    count++;
                }
            }
            return count;
        }

        private boolean hasCode(int line, int start, int end, List<SourceSpan> excluded) {
            for (int offset = start; offset < end; offset++) {
                if ((forcedToken[offset] || !ignored[offset])
                        && !Character.isWhitespace(source.charAt(offset))
                        && !isExcluded(offset, excluded)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isExcluded(int offset, List<SourceSpan> excluded) {
            for (SourceSpan span : excluded) {
                if (offset >= span.startOffset() && offset < span.endOffset()) return true;
            }
            return false;
        }

        private boolean hasPhysicalContent(int start, int end, List<SourceSpan> excluded) {
            boolean intersectsExcluded = false;
            for (SourceSpan span : excluded) {
                if (span.startOffset() < end && span.endOffset() > start) {
                    intersectsExcluded = true;
                    break;
                }
            }
            if (!intersectsExcluded) return end > start;
            for (int offset = start; offset < end; offset++) {
                if (!isExcluded(offset, excluded)
                        && !Character.isWhitespace(source.charAt(offset))) return true;
            }
            return false;
        }

        private void classifyIgnoredRanges() {
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(source.toCharArray());
            CompilationUnit unit = (CompilationUnit) parser.createAST(null);
            for (Object value : unit.getCommentList()) {
                if (value instanceof Comment comment) markIgnored(comment);
            }
            unit.accept(new ASTVisitor() {
                @Override
                public boolean visit(TextBlock node) {
                    markIgnored(node);
                    forcedToken[node.getStartPosition()] = true;
                    forcedToken[node.getStartPosition() + node.getLength() - 1] = true;
                    return false;
                }
            });
        }

        private void markIgnored(ASTNode node) {
            int end = Math.min(source.length(), node.getStartPosition() + node.getLength());
            for (int offset = Math.max(0, node.getStartPosition()); offset < end; offset++) {
                ignored[offset] = true;
            }
        }

        private int lineOf(int offset) {
            int low = 0;
            int high = lineStarts.size() - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (lineStarts.get(mid) <= offset) low = mid + 1;
                else high = mid - 1;
            }
            return Math.max(0, high);
        }

        private int lineEnd(int line) {
            return line + 1 < lineStarts.size() ? lineStarts.get(line + 1) : source.length();
        }

        private static List<Integer> lineStarts(String source) {
            if (source.isEmpty()) return List.of();
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) == '\n' && i + 1 < source.length()) starts.add(i + 1);
            }
            return List.copyOf(starts);
        }
    }
}
