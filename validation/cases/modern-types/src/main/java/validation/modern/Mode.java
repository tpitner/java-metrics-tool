// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.modern;

public enum Mode {
    FAST,
    SAFE;

    public boolean fast() {
        return this == FAST;
    }
}
