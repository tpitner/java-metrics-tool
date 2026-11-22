// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.overloads;

public class Overloaded {
    private final String value;

    public Overloaded() {
        this("");
    }

    public Overloaded(String value) {
        this.value = value;
    }

    public String run() {
        return value;
    }

    public String run(int count) {
        return value.repeat(count);
    }

    public String run(String suffix) {
        return value + suffix;
    }

    public String run(int count, String suffix) {
        return run(count) + suffix;
    }
}
