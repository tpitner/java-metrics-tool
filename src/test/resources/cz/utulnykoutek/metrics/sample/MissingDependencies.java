// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.sample;

import com.example.missing.Foo;
import com.example.missing.Bar;
import com.example.missing.Baz;
import com.example.missing.Qux;

public class MissingDependencies extends Foo implements Bar {
    private Baz field = new Baz();

    public MissingDependencies(Qux q) throws SomeCheckedException {
        q.doSomething();
    }

    public Unknown compute(Unknown input) {
        return input;
    }
}
