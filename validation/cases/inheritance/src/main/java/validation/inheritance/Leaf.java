// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.inheritance;

public class Leaf extends Middle {
    @Override
    public int doubled() {
        return super.doubled() + 1;
    }
}
