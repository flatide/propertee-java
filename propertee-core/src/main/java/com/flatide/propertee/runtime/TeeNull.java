package com.flatide.propertee.runtime;

/**
 * The ProperTee {@code null} value (spec v0.8.0, ProperTee issue #4) — JSON's explicit
 * "no value", carried losslessly through {@code JSON_PARSE}/{@code JSON_FORMAT}.
 *
 * <p><b>No implicit null:</b> the language itself never produces this value — missing
 * arguments and bare {@code return} still yield {@code {}} (empty object). It enters a
 * program only through the {@code null} literal or through data (JSON, host values).
 *
 * <p>A dedicated singleton, NOT Java {@code null}: the interpreter uses Java null
 * internally (statement results, no-branch sentinels), so the language-level value must
 * be distinguishable. Immutable, so deep-copy passes it through by reference. (Named
 * TeeNull rather than JsonNull to avoid colliding with Gson's {@code JsonNull}.)
 */
public final class TeeNull {

    /** The single ProperTee null value. Compare with {@code ==}. */
    public static final TeeNull NULL = new TeeNull();

    private TeeNull() {}

    @Override
    public String toString() {
        return "null";
    }
}
