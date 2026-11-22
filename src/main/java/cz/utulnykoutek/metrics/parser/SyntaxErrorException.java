// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.parser;

import java.util.List;

/**
 * Thrown when the parsed source file contains syntax errors.
 *
 * <p>Unresolved dependencies do not trigger this exception, because the parser
 * does not resolve bindings and therefore never needs a classpath. Only
 * problems reported by the parser itself (see {@code CompilationUnit#getProblems()})
 * are treated as errors.
 */
public class SyntaxErrorException extends Exception {

    /** Formatted syntax errors. */
    private final List<String> errors;

    public SyntaxErrorException(List<String> errors) {
        super(errors.isEmpty()
                ? "syntax error"
                : errors.size() + " syntax error" + (errors.size() == 1 ? "" : "s") + ": " + errors.get(0));
        this.errors = List.copyOf(errors);
    }

    /** Formatted errors in the form {@code file:line:column: message}. */
    public List<String> getErrors() {
        return errors;
    }
}
