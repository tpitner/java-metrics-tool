// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.interfaces;

public interface Extended extends Root {
    default String label() {
        return "extended";
    }
}
