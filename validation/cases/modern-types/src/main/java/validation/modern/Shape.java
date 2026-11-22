// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.modern;

public sealed interface Shape permits Circle, Rectangle {
    double area();
}
