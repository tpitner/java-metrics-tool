// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.nested;

public class Outer {
    private final int value;

    public Outer(int value) {
        this.value = value;
    }

    public Runnable factory() {
        class Local implements Runnable {
            @Override
            public void run() {
                System.out.println(value);
            }
        }
        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                System.out.println(value + 1);
            }
        };
        return value == 0 ? anonymous : new Local();
    }

    public static class StaticNested {
        public int answer() {
            return 42;
        }
    }

    public class Inner {
        public int outerValue() {
            return value;
        }
    }
}
