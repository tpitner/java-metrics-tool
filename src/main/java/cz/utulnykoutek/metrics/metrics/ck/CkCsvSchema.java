// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.ck;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Exact CSV contract frozen for CK revision 8a1ef91 (0.7.1-SNAPSHOT). */
public final class CkCsvSchema {
    public static final String UPSTREAM_REVISION = "8a1ef91";
    public static final String UPSTREAM_VERSION = "0.7.1-SNAPSHOT";

    public static final List<String> CLASS_HEADER = List.of(
            "file", "class", "type",
            "cbo", "cboModified", "fanin", "fanout", "wmc", "dit", "noc", "rfc",
            "lcom", "lcom*", "tcc", "lcc",
            "totalMethodsQty", "staticMethodsQty", "publicMethodsQty", "privateMethodsQty",
            "protectedMethodsQty", "defaultMethodsQty", "visibleMethodsQty", "abstractMethodsQty",
            "finalMethodsQty", "synchronizedMethodsQty",
            "totalFieldsQty", "staticFieldsQty", "publicFieldsQty", "privateFieldsQty",
            "protectedFieldsQty", "defaultFieldsQty", "finalFieldsQty", "synchronizedFieldsQty",
            "nosi", "loc", "returnQty", "loopQty", "comparisonsQty", "tryCatchQty",
            "parenthesizedExpsQty", "stringLiteralsQty", "numbersQty", "assignmentsQty",
            "mathOperationsQty", "variablesQty", "maxNestedBlocksQty", "anonymousClassesQty",
            "innerClassesQty", "lambdasQty", "uniqueWordsQty", "modifiers", "logStatementsQty");

    public static final List<String> METHOD_HEADER = List.of(
            "file", "class", "method", "constructor", "line",
            "cbo", "cboModified", "fanin", "fanout", "wmc", "rfc", "loc",
            "returnsQty", "variablesQty", "parametersQty", "methodsInvokedQty",
            "methodsInvokedLocalQty", "methodsInvokedIndirectLocalQty", "loopQty",
            "comparisonsQty", "tryCatchQty", "parenthesizedExpsQty", "stringLiteralsQty",
            "numbersQty", "assignmentsQty", "mathOperationsQty", "maxNestedBlocksQty",
            "anonymousClassesQty", "innerClassesQty", "lambdasQty", "uniqueWordsQty",
            "modifiers", "logStatementsQty", "hasJavaDoc");

    public static final String CLASS_HEADER_SHA256 = fingerprint(CLASS_HEADER);
    public static final String METHOD_HEADER_SHA256 = fingerprint(METHOD_HEADER);

    private CkCsvSchema() {
    }

    private static String fingerprint(List<String> header) {
        try {
            byte[] bytes = String.join(",", header).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
