// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.ck;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads CK CSV only when its complete ordered header matches the frozen schema. */
public final class CkCsvReader {
    public List<ClassRow> readClasses(Path file) {
        List<Map<String, String>> records = read(file, CkCsvSchema.CLASS_HEADER, "class.csv");
        return records.stream().map(values -> new ClassRow(
                values.get("file"), values.get("class"), values.get("type"), values)).toList();
    }

    public List<MethodRow> readMethods(Path file) {
        List<Map<String, String>> records = read(file, CkCsvSchema.METHOD_HEADER, "method.csv");
        List<MethodRow> result = new ArrayList<>();
        for (Map<String, String> values : records) {
            boolean constructor = parseBoolean(values.get("constructor"), file, "constructor");
            int line = parseInteger(values.get("line"), file, "line");
            result.add(new MethodRow(
                    values.get("file"), values.get("class"), values.get("method"),
                    constructor, line, values));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, String>> read(
            Path file,
            List<String> expectedHeader,
            String label) {
        try (CSVParser parser = CSVParser.parse(
                file,
                StandardCharsets.UTF_8,
                CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            List<String> actualHeader = parser.getHeaderMap().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .toList();
            if (!actualHeader.equals(expectedHeader)) {
                throw new CkProviderException(label + " header mismatch; expected "
                        + expectedHeader + " but got " + actualHeader);
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (record.size() != expectedHeader.size()) {
                    throw new CkProviderException(label + " record " + record.getRecordNumber()
                            + " has " + record.size() + " columns; expected " + expectedHeader.size());
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String column : expectedHeader) values.put(column, record.get(column));
                rows.add(Map.copyOf(values));
            }
            return List.copyOf(rows);
        } catch (CkProviderException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw new CkProviderException("Cannot read CK " + label + " from " + file, e);
        }
    }

    private static boolean parseBoolean(String value, Path file, String column) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new CkProviderException("Invalid boolean in " + file + " column " + column + ": " + value);
    }

    private static int parseInteger(String value, Path file, String column) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CkProviderException("Invalid integer in " + file + " column " + column + ": " + value, e);
        }
    }

    public record ClassRow(String file, String className, String type, Map<String, String> values) {
        public ClassRow {
            values = Map.copyOf(values);
        }
    }

    public record MethodRow(
            String file,
            String className,
            String method,
            boolean constructor,
            int line,
            Map<String, String> values) {
        public MethodRow {
            values = Map.copyOf(values);
        }
    }
}
