// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.initializers;

import java.util.function.IntUnaryOperator;

public class InitLambda {
    static {
        System.setProperty("validation.mode", "test");
    }

    {
        Runnable task = () -> {
            System.out.println("initialized");
        };
        task.run();
    }

    private final IntUnaryOperator increment =
            value -> value + 1;

    public int apply(int value) {
        return increment.applyAsInt(value);
    }
}
