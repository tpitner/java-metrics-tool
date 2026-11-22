// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.inheritance;

public class Middle extends Base {
    public Middle() {
        super(1);
    }

    public int doubled() {
        return value * 2;
    }
}
