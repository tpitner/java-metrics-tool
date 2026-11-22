// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.sample;

public class Outer {
    int f1, f2;
    String f3;

    public Outer() {}

    public void m1() {}
    private int m2(int x) { return x; }

    static class Inner {
        double a;
        int b;

        public Inner() {}

        public void i1() {}
        public void i2() {}
        public void i3() {}

        class DeepInner {
            public void d1() {}
            public void d2() {}
        }
    }
}
