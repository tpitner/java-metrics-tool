// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.metrics.ck.CkCsvReader;
import cz.utulnykoutek.metrics.metrics.ck.CkCsvSchema;
import cz.utulnykoutek.metrics.metrics.ck.CkProviderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CkCsvReaderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void frozenHeadersHaveExpectedFingerprints() {
        assertEquals("1ed33619a8815149d4ae3973fc7b1d857da52e2b182726587e396ef551a2d91a",
                CkCsvSchema.CLASS_HEADER_SHA256);
        assertEquals("cfa9b02259c3a37169535fc92868cf7d71ca2577e0cb148327daf7fd75e43fb8",
                CkCsvSchema.METHOD_HEADER_SHA256);
    }

    @Test
    void readsQuotedMethodIdentityAgainstCompleteFrozenSchema() throws Exception {
        Path csv = temporaryDirectory.resolve("method.csv");
        List<String> values = defaults(CkCsvSchema.METHOD_HEADER.size());
        put(values, CkCsvSchema.METHOD_HEADER, "file", "/tmp/Util.java");
        put(values, CkCsvSchema.METHOD_HEADER, "class", "com.example.Util");
        put(values, CkCsvSchema.METHOD_HEADER, "method", "\"add/2[int,int]\"");
        put(values, CkCsvSchema.METHOD_HEADER, "constructor", "false");
        put(values, CkCsvSchema.METHOD_HEADER, "line", "7");
        put(values, CkCsvSchema.METHOD_HEADER, "hasJavaDoc", "false");
        Files.writeString(csv, String.join(",", CkCsvSchema.METHOD_HEADER) + "\r\n"
                + String.join(",", values) + "\r\n");

        CkCsvReader.MethodRow row = new CkCsvReader().readMethods(csv).getFirst();

        assertEquals("add/2[int,int]", row.method());
        assertEquals(7, row.line());
        assertEquals("0", row.values().get("wmc"));
    }

    @Test
    void rejectsAnyHeaderDrift() throws Exception {
        Path csv = temporaryDirectory.resolve("class.csv");
        Files.writeString(csv, "file,class,type,cbo,unexpected\n");

        assertThrows(CkProviderException.class, () -> new CkCsvReader().readClasses(csv));
    }

    private static List<String> defaults(int size) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < size; i++) values.add("0");
        return values;
    }

    private static void put(List<String> values, List<String> header, String column, String value) {
        values.set(header.indexOf(column), value);
    }
}
