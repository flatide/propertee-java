package com.flatide.propertee.runtime;

import java.util.LinkedHashMap;

/**
 * A <b>genuine Result</b> (spec v0.10.0) — the {@link Result} shape whose runtime origin
 * is remembered. It IS the plain object it looks like (a {@code LinkedHashMap}), so every
 * existing path — display, JSON (Gson serializes the Map interface), member access,
 * equality, {@code TYPE_OF} — is unchanged; only {@code IS_RESULT} and {@code UNWRAP}
 * observe the type.
 *
 * <p>Origin rules: created only by the {@link Result} factory (built-ins, external-function
 * wrapping, thread results) and the {@code OK}/{@code ERR} constructors.
 * {@link TypeChecker#deepCopy} preserves it; field mutation does not remove it; JSON never
 * produces it (the origin does not round-trip).
 */
public final class TeeResult extends LinkedHashMap<String, Object> {
    TeeResult() {}
}
