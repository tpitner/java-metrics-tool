// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package validation.coupling.client;

import validation.coupling.api.Service;

public class Controller {
    private final Service service;

    public Controller(Service service) {
        this.service = service;
    }

    public String handle(String input) {
        return service.execute(input);
    }
}
