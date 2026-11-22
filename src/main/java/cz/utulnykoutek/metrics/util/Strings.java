// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.util;

/** Small string validation helpers shared across the codebase. */
public final class Strings {
    private Strings() {}

    /**
     * Returns {@code value} if it is neither {@code null} nor blank; otherwise
     * throws {@link IllegalArgumentException} with a message of the form
     * {@code name + " must not be blank"}.
     */
    public static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /**
     * Throws {@link IllegalArgumentException} with the given {@code message}
     * when {@code value} is {@code null} or blank.
     */
    public static void ensureNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Returns {@code value} if it is not {@code null}; otherwise throws
     * {@link NullPointerException} with {@code name} as its detail message.
     */
    public static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        return value;
    }

    /**
     * Throws {@link NullPointerException} with the given {@code message} when
     * {@code value} is {@code null}.
     */
    public static void ensureNonNull(Object value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
    }
}