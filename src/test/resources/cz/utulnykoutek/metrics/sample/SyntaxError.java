// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.sample;

public class SyntaxError {
    private int x = ;

    public void broken( {
        if (x > 0 {
            System.out.println("hi")
        }
    }

    public void ok() {}
}
