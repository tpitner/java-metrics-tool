// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.cli.Main;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MavenProjectIntegrationTest {

    @Test
    void analyzesSmallMavenProjectViaPom() throws Exception {
        Path pom = Path.of("src/test/resources/maven-it/small/pom.xml").toAbsolutePath().normalize();
        assertTrue(pom.toFile().isFile(), "test pom must exist: " + pom);

        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        int code;
        try {
            code = Main.run(new String[]{"--pom", pom.toString(), "--format", "json", "--no-compile"});
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }

        String stdout = out.toString(StandardCharsets.UTF_8);
        String stderr = err.toString(StandardCharsets.UTF_8);
        assertEquals(0, code, "stderr:\n" + stderr + "\nstdout:\n" + stdout);
        assertTrue(stdout.contains("\"qualifiedName\": \"com.example.demo\""), stdout);
        assertTrue(stdout.contains("\"name\": \"App\""), stdout);
        assertTrue(stdout.contains("\"name\": \"Util\""), stdout);
        assertTrue(stdout.contains("\"baseline\""), stdout);
        assertTrue(stdout.contains("\"jdtCoreVersion\""), stdout);
        assertTrue(stdout.contains("\"jdkVersion\""), stdout);
        assertTrue(stdout.contains("\"parser\""), stdout);
        assertTrue(stdout.contains("\"resolveBindings\": true"), stdout);
        assertTrue(stdout.contains("\"coverage\""), stdout);
        assertTrue(stdout.contains("\"relations\""), stdout);
        assertTrue(stdout.contains("\"dependencyEdges\""), stdout);
        assertTrue(stdout.contains("\"providerRuns\""), stdout);
        assertTrue(stdout.contains("\"measurements\""), stdout);
    }
}
