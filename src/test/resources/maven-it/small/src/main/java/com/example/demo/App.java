// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package com.example.demo;

public class App {
    private String name;

    public App(String name) {
        this.name = name;
    }

    public String greet() {
        return "hi " + name;
    }
}
