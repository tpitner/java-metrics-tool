// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.cli;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.config.CliOverrides;
import cz.utulnykoutek.metrics.config.ConfigException;
import cz.utulnykoutek.metrics.config.ConfigLoader;
import cz.utulnykoutek.metrics.config.ConfigValidator;
import cz.utulnykoutek.metrics.config.EffectiveConfig;
import cz.utulnykoutek.metrics.config.EffectiveConfigResolver;
import cz.utulnykoutek.metrics.config.LoadedToolConfig;
import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.export.JsonExporter;
import cz.utulnykoutek.metrics.export.TextExporter;
import cz.utulnykoutek.metrics.export.YamlPreviewConverter;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.JavaMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.ck.CkMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ck.CkProviderException;
import cz.utulnykoutek.metrics.parser.JdtParser;
import cz.utulnykoutek.metrics.parser.PackageMerger;
import cz.utulnykoutek.metrics.parser.SyntaxErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Command-line entry point for java-metrics.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        CliOverrides cli;
        EffectiveConfig effective;
        try {
            cli = CliOverrides.parse(args);
            LoadedToolConfig loaded = ConfigLoader.load(cli.configFile(), workingDirectory);
            effective = EffectiveConfigResolver.resolve(loaded, cli, workingDirectory);
        } catch (ConfigException e) {
            e.errors().forEach(error -> System.err.println("Error: " + error));
            usage();
            return 2;
        }

        if (cli.printEffectiveConfig()) {
            System.out.print(effective.provenance().effectiveToml());
        }
        if (cli.validateConfig() && !cli.printEffectiveConfig()) {
            System.out.println("Configuration is valid (SHA-256 "
                    + effective.provenance().effectiveSha256() + ").");
        }
        if (cli.printEffectiveConfig() || cli.validateConfig()) return 0;
        List<Path> sourcepaths = new ArrayList<>(effective.project().sourcepath());
        List<Path> classpaths = new ArrayList<>(effective.project().classpath());
        String release = effective.project().release().orElse(null);
        Path pomPath = effective.project().pom().orElse(null);

        if (pomPath != null) {
            try {
                MavenProject.Resolved mvn = MavenProject.resolve(
                        pomPath, effective.project().includeTests(), effective.project().compile());
                classpaths.addAll(mvn.classpath);
                sourcepaths.addAll(mvn.sourceRoots);
                if (mvn.release != null && (release == null || release.isBlank())) {
                    release = mvn.release;
                }
            } catch (Exception e) {
                System.err.println("Error resolving Maven project: " + e.getMessage());
                return 2;
            }
        }
        if (release != null && !release.isBlank()) {
            var releaseError = ConfigValidator.sourceReleaseError(release);
            if (releaseError.isPresent()) {
                System.err.println("Error: " + releaseError.orElseThrow());
                return 2;
            }
        }

        List<Path> files = new ArrayList<>();
        try {
            for (Path input : effective.project().inputs()) {
                collect(input, effective.project().recursive(), files);
            }
            for (Path sp : sourcepaths) {
                collect(sp, true, files);
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }

        Set<Path> unique = new LinkedHashSet<>();
        for (Path f : files) {
            unique.add(f.toAbsolutePath().normalize());
        }
        files = new ArrayList<>(unique);
        files.sort(java.util.Comparator.naturalOrder());

        if (files.isEmpty()) {
            System.err.println("Error: no .java files found.");
            return 2;
        }

        JdtParser.Config cfg = new JdtParser.Config();
        cfg.classpath = classpaths;
        cfg.sourcepath = sourcepaths;
        cfg.release = release;
        BaselineMetadata metadata = BaselineMetadata.capture(cfg, effective.provenance());
        JdtParser parser = new JdtParser(cfg);
        List<PackageCompound> packages = new ArrayList<>();
        List<String> syntaxErrors = new ArrayList<>();
        boolean failed = false;

        for (Path file : files) {
            try {
                Compound root = parser.parse(file);
                if (root instanceof PackageCompound pkg) {
                    packages.add(pkg);
                }
            } catch (SyntaxErrorException e) {
                syntaxErrors.addAll(e.getErrors());
                failed = true;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 2;
            }
        }

        List<PackageCompound> merged = PackageMerger.merge(packages);
        var selectedJavaMetrics = effective.providers().java().enabled()
                ? effective.providers().java().metrics()
                : java.util.Set.<cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition>of();
        AnalysisContext analysisContext = new AnalysisContext(merged, files, metadata);
        ProviderResult javaMetrics = new JavaMetricsProvider(selectedJavaMetrics).measure(analysisContext);
        List<ProviderResult> providerResults = new ArrayList<>();
        if (effective.providers().java().enabled()) providerResults.add(javaMetrics);
        if (effective.providers().ck().enabled()) {
            Path ckProject = ckProjectDirectory(effective, files);
            try {
                ProviderResult ckMetrics = new CkMetricsProvider(
                        effective.providers().ck(),
                        ckProject,
                        effective.output().providerArtifactsDirectory(),
                        effective.output().retainProviderArtifacts())
                        .measure(analysisContext);
                providerResults.add(ckMetrics);
                ckMetrics.diagnostics().forEach(message -> System.err.println("CK: " + message));
                if (!"json".equals(effective.output().format())) {
                    System.err.println("CK: external measurements are serialized only by the JSON format; "
                            + "text/preview remains a structural view.");
                }
            } catch (CkProviderException e) {
                System.err.println("Error running CK: " + e.getMessage());
                return 2;
            }
        }
        StringBuilder renderedOutput = new StringBuilder();
        for (PackageCompound pkg : merged) {
            String rendered = switch (effective.output().format()) {
                case "json" -> JsonExporter.render(pkg, metadata, javaMetrics, providerResults);
                case "preview", "ansi", "yaml-preview" -> YamlPreviewConverter.render(pkg, javaMetrics);
                default -> TextExporter.render(pkg, javaMetrics);
            };
            renderedOutput.append(rendered);
            if (!rendered.endsWith("\n")) renderedOutput.append('\n');
        }
        if (effective.output().writesToStdout()) {
            System.out.print(renderedOutput);
        } else {
            try {
                Path destination = effective.output().destination().orElseThrow();
                Path parent = destination.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(destination, renderedOutput);
            } catch (IOException e) {
                System.err.println("Error writing output: " + e.getMessage());
                return 2;
            }
        }

        if (failed) {
            for (String err : syntaxErrors) {
                System.err.println(err);
            }
            return 1;
        }
        return 0;
    }

    private static void collect(Path root, boolean recursive, List<Path> out) throws IOException {
        if (Files.isRegularFile(root) && root.toString().endsWith(".java")) {
            out.add(root);
            return;
        }
        if (Files.isDirectory(root)) {
            try (var stream = recursive ? Files.walk(root) : Files.list(root)) {
                stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                        .forEach(out::add);
            }
            return;
        }
        throw new IOException("Not a file or directory: " + root);
    }

    private static Path ckProjectDirectory(EffectiveConfig effective, List<Path> sourceFiles) {
        if (effective.project().pom().isPresent()) {
            Path parent = effective.project().pom().orElseThrow().getParent();
            if (parent != null) return parent;
        }
        List<Path> candidates = effective.project().inputs().isEmpty()
                ? sourceFiles
                : effective.project().inputs();
        Path common = null;
        for (Path candidate : candidates) {
            Path path = Files.isDirectory(candidate) ? candidate : candidate.getParent();
            if (path == null) continue;
            path = path.toAbsolutePath().normalize();
            common = common == null ? path : commonAncestor(common, path);
        }
        if (common == null) throw new CkProviderException("Cannot determine CK project directory");
        return common;
    }

    private static Path commonAncestor(Path first, Path second) {
        Path candidate = first;
        while (candidate != null && !second.startsWith(candidate)) candidate = candidate.getParent();
        if (candidate == null) throw new CkProviderException(
                "CK inputs do not share a filesystem root: " + first + " and " + second);
        return candidate;
    }

    private static void usage() {
        System.err.println("Usage: java-metrics [<file.java|directory> ...] [--config <file.toml>] [--validate-config] [--print-effective-config] [--pom <pom.xml>] [--format text|json|preview] [--sourcepath <dir[:dir]>] [--classpath <jar[:jar]>] [--release <level>] [--recursive|--no-recursive] [--include-tests] [--no-compile]");
    }
}
