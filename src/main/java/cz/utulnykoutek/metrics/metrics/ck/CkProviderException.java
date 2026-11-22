// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.ck;

/** Fatal CK process, schema or import failure. */
public final class CkProviderException extends RuntimeException {
    public CkProviderException(String message) {
        super(message);
    }

    public CkProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
