// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainCliContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void returnsZeroForSuccessfulAnalysis() throws Exception {
        Path input = copySample("Nested.java");

        CliResult result = run(input.toString(), "--release", "17", "--format", "json");

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stderr().isEmpty(), result.stderr());
        assertTrue(result.stdout().contains("\"qualifiedName\": \"cz.utulnykoutek.metrics.sample.Outer\""),
                result.stdout());
    }

    @Test
    void returnsOneWhenJavaSourceHasSyntaxErrors() throws Exception {
        Path input = copySample("SyntaxError.java");

        CliResult result = run(input.toString(), "--release", "17", "--format", "json");

        assertEquals(1, result.exitCode(), result.stderr());
        assertFalse(result.stderr().isBlank());
    }

    @Test
    void returnsTwoForInvalidInputConfiguration() {
        Path missing = temporaryDirectory.resolve("missing.java");

        CliResult result = run(missing.toString(), "--format", "json");

        assertEquals(2, result.exitCode(), result.stderr());
        assertTrue(result.stderr().contains("project.inputs entry is not a file or directory"),
                result.stderr());
    }

    @Test
    void rendersIdenticalJsonForRepeatedRuns() throws Exception {
        Path input = copySample("Nested.java");
        String[] args = {input.toString(), "--release", "17", "--format", "json"};

        CliResult first = run(args);
        CliResult second = run(args);

        assertEquals(0, first.exitCode(), first.stderr());
        assertEquals(0, second.exitCode(), second.stderr());
        assertEquals(first.stdout(), second.stdout());
        assertEquals(first.stderr(), second.stderr());
    }

    private Path copySample(String name) throws Exception {
        Path source = Path.of(getClass().getResource(
                "/cz/utulnykoutek/metrics/sample/" + name).toURI());
        return Files.copy(source, temporaryDirectory.resolve(name));
    }

    private static CliResult run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
            int exitCode = Main.run(args);
            return new CliResult(
                    exitCode,
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record CliResult(int exitCode, String stdout, String stderr) {
    }
}
