// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.basic;

public class Members {
    private int count;
    private String name;

    public Members() {
        this("default");
    }

    public Members(String name) {
        this.name = name;
    }

    public int count() {
        return count;
    }

    public String name() {
        return name;
    }

    public void increment() {
        count++;
    }
}
