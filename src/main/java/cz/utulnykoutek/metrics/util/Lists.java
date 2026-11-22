// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.util;

import java.util.List;
import java.util.function.Predicate;

/** Small collection helpers shared across the codebase. */
public final class Lists {
    private Lists() {}

    /** Returns a new list containing the elements of {@code source} that satisfy {@code condition}. */
    public static <T> List<T> filtered(List<T> source, Predicate<? super T> condition) {
        return source.stream().filter(condition).toList();
    }
}