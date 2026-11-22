// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.ck;

import cz.utulnykoutek.metrics.config.CkProviderConfig;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.MeasurementProvider;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.ProviderRunMetadata;
import cz.utulnykoutek.metrics.metrics.definition.MetricCatalog;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementResult;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementStore;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.metrics.measurement.UnavailableResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Imports metrics from an isolated run of the pinned CK standalone JAR. */
public final class CkMetricsProvider implements MeasurementProvider {
    public static final String ID = "ck";
    private static final Set<CkMetricDefinition> DEFINITIONS =
            Set.copyOf(EnumSet.allOf(CkMetricDefinition.class));

    private final CkProviderConfig config;
    private final Path projectDirectory;
    private final Path artifactDirectory;
    private final boolean retainArtifacts;
    private final CkProcessRunner runner;
    private final CkCsvReader csvReader;

    public CkMetricsProvider(
            CkProviderConfig config,
            Path projectDirectory,
            Path artifactDirectory,
            boolean retainArtifacts) {
        this(config, projectDirectory, artifactDirectory, retainArtifacts,
                new CkProcessRunner(), new CkCsvReader());
    }

    CkMetricsProvider(
            CkProviderConfig config,
            Path projectDirectory,
            Path artifactDirectory,
            boolean retainArtifacts,
            CkProcessRunner runner,
            CkCsvReader csvReader) {
        this.config = config;
        this.projectDirectory = projectDirectory.toAbsolutePath().normalize();
        this.artifactDirectory = artifactDirectory.toAbsolutePath().normalize();
        this.retainArtifacts = retainArtifacts;
        this.runner = runner;
        this.csvReader = csvReader;
    }

    @Override public String id() { return ID; }
    @Override public Set<CkMetricDefinition> definitions() { return DEFINITIONS; }

    @Override
    public ProviderResult measure(AnalysisContext context) {
        CkSubjectMapper mapper = new CkSubjectMapper(context);
        boolean keepRaw = retainArtifacts && config.retainRawCsv();
        try (CkProcessResult process = runner.run(
                config, projectDirectory, artifactDirectory, keepRaw)) {
            if (process.stderr().contains("Error in ")) {
                throw new CkProviderException(
                        "CK reported at least one source-level analysis error; refusing partial exact results");
            }
            List<CkCsvReader.ClassRow> classes = csvReader.readClasses(process.classCsv());
            List<CkCsvReader.MethodRow> methods = csvReader.readMethods(process.methodCsv());
            String providerRunId = providerRunId(process.jarSha256());
            MetricCatalog catalog = MetricCatalog.builder().registerAll(DEFINITIONS).build();
            MeasurementStore.Builder measurements = MeasurementStore.builder(catalog);

            for (CkCsvReader.ClassRow row : classes) {
                Optional<SubjectRef> subject = mapper.type(row.className());
                if (subject.isEmpty()) continue;
                for (CkMetricDefinition definition : DEFINITIONS) {
                    definition.classColumn().ifPresent(column -> measurements.add(measurement(
                            definition, subject.orElseThrow(), providerRunId, row.values().get(column))));
                }
            }
            for (CkCsvReader.MethodRow row : methods) {
                Optional<SubjectRef> subject = mapper.method(
                        row.className(), row.method(), row.constructor());
                if (subject.isEmpty()) continue;
                for (CkMetricDefinition definition : DEFINITIONS) {
                    definition.methodColumn().ifPresent(column -> measurements.add(measurement(
                            definition, subject.orElseThrow(), providerRunId, row.values().get(column))));
                }
            }

            List<String> diagnostics = new ArrayList<>(mapper.diagnostics());
            ProviderRunMetadata run = new ProviderRunMetadata(
                    ID,
                    providerRunId,
                    process.ckVersion(),
                    metadata(process, keepRaw));
            return new ProviderResult(run, measurements.build(), diagnostics);
        } catch (CkProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CkProviderException("Cannot import CK measurements", e);
        }
    }

    private static MetricMeasurement measurement(
            CkMetricDefinition definition,
            SubjectRef subject,
            String providerRunId,
            String rawValue) {
        return new MetricMeasurement(
                definition.id(), subject, providerRunId, result(rawValue));
    }

    private static MeasurementResult result(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return new UnavailableResult(Set.of(ReasonCode.PROVIDER_DID_NOT_REPORT_VALUE));
        }
        if ("NaN".equalsIgnoreCase(rawValue)) {
            return new NotApplicableResult(ReasonCode.NO_ELIGIBLE_MEMBERS);
        }
        try {
            BigDecimal value = new BigDecimal(rawValue);
            if (value.signum() < 0) {
                return new UnavailableResult(Set.of(ReasonCode.PROVIDER_DID_NOT_REPORT_VALUE));
            }
            return NumericResult.exact(value);
        } catch (NumberFormatException e) {
            throw new CkProviderException("CK reported a non-numeric metric value: " + rawValue, e);
        }
    }

    private Map<String, String> metadata(CkProcessResult process, boolean keepRaw) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("jar", config.jar().orElseThrow().toString());
        values.put("jarSha256", process.jarSha256());
        values.put("upstreamRevision", CkCsvSchema.UPSTREAM_REVISION);
        values.put("classSchemaSha256", CkCsvSchema.CLASS_HEADER_SHA256);
        values.put("methodSchemaSha256", CkCsvSchema.METHOD_HEADER_SHA256);
        values.put("projectDirectory", projectDirectory.toString());
        values.put("command", process.command().toString());
        values.put("javaVersion", process.javaVersion());
        values.put("durationMillis", Long.toString(process.duration().toMillis()));
        values.put("useJars", Boolean.toString(config.useJars()));
        values.put("maxFilesPerPartition", Integer.toString(config.maxFilesPerPartition()));
        values.put("variablesAndFields", Boolean.toString(config.variablesAndFields()));
        values.put("ignoredDirectories", config.ignoredDirectories().toString());
        values.put("rawArtifactsRetained", Boolean.toString(keepRaw));
        if (keepRaw) values.put("rawArtifactsDirectory", process.outputDirectory().toString());
        return values;
    }

    private String providerRunId(String jarSha256) {
        String stableConfig = jarSha256 + "\n" + projectDirectory + "\n"
                + config.useJars() + "\n" + config.maxFilesPerPartition() + "\n"
                + config.variablesAndFields() + "\n" + config.ignoredDirectories();
        return ID + ":" + CkCsvSchema.UPSTREAM_VERSION + ":" + sha256(stableConfig).substring(0, 16);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
