// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.coupling.impl;

import validation.coupling.api.Service;

public class ServiceImpl implements Service {
    private final Helper helper;

    public ServiceImpl(Helper helper) {
        this.helper = helper;
    }

    @Override
    public String execute(String input) {
        return helper.normalize(input);
    }
}
