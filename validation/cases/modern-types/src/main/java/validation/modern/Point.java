// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.modern;

public record Point(int x, int y) {
    public int sum() {
        return x + y;
    }
}
