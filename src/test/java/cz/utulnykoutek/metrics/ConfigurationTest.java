// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.cli.Main;
import cz.utulnykoutek.metrics.config.CliOverrides;
import cz.utulnykoutek.metrics.config.ConfigException;
import cz.utulnykoutek.metrics.config.ConfigLoader;
import cz.utulnykoutek.metrics.config.EffectiveConfig;
import cz.utulnykoutek.metrics.config.EffectiveConfigResolver;
import cz.utulnykoutek.metrics.config.LoadedToolConfig;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesTomlPathsAndAppliesExplicitCliOverrides() throws Exception {
        Path input = copySample("Nested.java");
        Path config = writeConfig("""
                schema-version = 1

                [project]
                inputs = ["input/Nested.java"]
                recursive = false
                release = "17"

                [output]
                format = "text"
                destination = "reports/metrics.json"

                [providers.java]
                enabled = true
                metrics = ["java.declared-methods", "java.declared-fields"]

                [providers.ck]
                enabled = false
                timeout-seconds = 30
                """);

        CliOverrides cli = CliOverrides.parse(new String[]{
                "--config", config.toString(), "--format", "json"});
        LoadedToolConfig loaded = ConfigLoader.load(cli.configFile(), temporaryDirectory);
        EffectiveConfig effective = EffectiveConfigResolver.resolve(loaded, cli, temporaryDirectory);

        assertEquals(List.of(input), effective.project().inputs());
        assertEquals("json", effective.output().format());
        assertEquals(temporaryDirectory.resolve("reports/metrics.json"),
                effective.output().destination().orElseThrow());
        assertEquals(Set.of(
                BuiltInMetricDefinition.DECLARED_METHODS,
                BuiltInMetricDefinition.DECLARED_FIELDS), effective.providers().java().metrics());
        assertEquals(List.of("output.format"), effective.provenance().cliOverrides());
        assertTrue(effective.provenance().sourceSha256().orElseThrow().matches("[0-9a-f]{64}"));
        assertTrue(effective.provenance().effectiveSha256().matches("[0-9a-f]{64}"));
        assertTrue(effective.provenance().effectiveToml().contains(
                "inputs = [\"" + input + "\"]"));

        EffectiveConfig repeated = EffectiveConfigResolver.resolve(loaded, cli, temporaryDirectory);
        assertEquals(effective.provenance().effectiveToml(), repeated.provenance().effectiveToml());
        assertEquals(effective.provenance().effectiveSha256(), repeated.provenance().effectiveSha256());
    }

    @Test
    void rejectsUnknownKeysAndWrongTypes() throws Exception {
        Path config = writeConfig("""
                schema-version = 1
                surprise = true

                [project]
                inputs = "not-an-array"
                """);

        ConfigException error = assertThrows(ConfigException.class,
                () -> ConfigLoader.load(java.util.Optional.of(config), temporaryDirectory));

        assertTrue(error.errors().stream().anyMatch(message -> message.contains("Unknown configuration key: surprise")));
        assertTrue(error.errors().stream().anyMatch(message -> message.contains("project.inputs must be an array")));
    }

    @Test
    void rejectsCkWithoutVariableAndFieldMetrics() throws Exception {
        Path jar = Files.createFile(temporaryDirectory.resolve("ck.jar"));
        Path config = writeConfig("""
                schema-version = 1

                [providers.ck]
                enabled = true
                jar = "%s"
                variables-and-fields = false
                """.formatted(jar));

        LoadedToolConfig loaded = ConfigLoader.load(
                java.util.Optional.of(config), temporaryDirectory);
        ConfigException error = assertThrows(ConfigException.class,
                () -> EffectiveConfigResolver.resolve(
                        loaded, CliOverrides.parse(new String[]{"--config", config.toString()}),
                        temporaryDirectory));

        assertTrue(error.errors().contains(
                "providers.ck.variables-and-fields must be true because CK TCC/LCC depend on method field usage"));
    }

    @Test
    void reportsMissingOptionValueWithoutSwallowingFollowingOption() {
        ConfigException error = assertThrows(ConfigException.class,
                () -> CliOverrides.parse(new String[]{"--format", "--unknown"}));

        assertTrue(error.errors().contains("--format requires a value"));
        assertTrue(error.errors().contains("Unknown option: --unknown"));
    }

    @Test
    void runsEndToEndFromTomlAndRecordsConfigurationProvenance() throws Exception {
        copySample("Nested.java");
        Path config = writeConfig("""
                schema-version = 1

                [project]
                inputs = ["input/Nested.java"]

                [output]
                format = "json"
                destination = "reports/result.json"

                [providers.java]
                metrics = ["java.declared-methods"]
                """);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = captureMain(new String[]{"--config", config.toString()}, stdout, stderr);

        Path result = temporaryDirectory.resolve("reports/result.json");
        assertEquals(0, code, stderr.toString(StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(result));
        String json = Files.readString(result);
        assertTrue(json.contains("\"configuration\""), json);
        assertTrue(json.contains("\"effectiveSha256\""), json);
        assertTrue(json.contains("java.declared-methods"), json);
        assertFalse(json.contains("java.declared-fields"), json);
    }

    private Path copySample(String name) throws Exception {
        Path inputDirectory = Files.createDirectories(temporaryDirectory.resolve("input"));
        Path source = Path.of(getClass().getResource(
                "/cz/utulnykoutek/metrics/sample/" + name).toURI());
        return Files.copy(source, inputDirectory.resolve(name));
    }

    private Path writeConfig(String content) throws Exception {
        Path config = temporaryDirectory.resolve("java-metrics.toml");
        return Files.writeString(config, content);
    }

    private static int captureMain(
            String[] args,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream stderr) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
            return Main.run(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
