// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.inheritance;

public class Base {
    protected int value;

    public Base(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
