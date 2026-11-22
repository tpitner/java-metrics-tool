// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.util.List;

/** Aggregated syntax, type and semantic errors found while loading configuration. */
public final class ConfigException extends Exception {
    private final List<String> errors;

    public ConfigException(List<String> errors) {
        super(errors == null ? "Configuration errors are missing"
                : String.join(System.lineSeparator(), errors));
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Configuration errors must not be empty");
        }
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
