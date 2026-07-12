package com.flatide.propertee.tests;

import com.flatide.propertee.interpreter.BuiltinFunctions;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Spec v0.13.0: interpreter-dispatched names (PRINT, SLEEP, FAIL, UNWRAP) cannot be replaced by
 * host registrations — the registration itself is a host-API error (previously PRINT/SLEEP were
 * implementation-defined and FAIL/UNWRAP silently lost to the built-ins).
 */
public class DispatchNameGuardTest {

    private static final BuiltinFunctions.BuiltinFunction NOOP = new BuiltinFunctions.BuiltinFunction() {
        @Override
        public Object call(List<Object> args) {
            return 1;
        }
    };

    @Test
    public void interpreterDispatchedNamesAreRejected() {
        BuiltinFunctions b = newBuiltins();
        for (String name : new String[] { "PRINT", "SLEEP", "FAIL", "UNWRAP" }) {
            try {
                b.register(name, NOOP);
                Assert.fail("register(" + name + ") should be a host-API error");
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("'" + name + "'"));
            }
        }
        try {
            b.registerExternal("SLEEP", NOOP);
            Assert.fail("registerExternal(SLEEP) should be a host-API error");
        } catch (IllegalArgumentException expected) {
        }
        try {
            b.registerExternalAsync("FAIL", NOOP);
            Assert.fail("registerExternalAsync(FAIL) should be a host-API error");
        } catch (IllegalArgumentException expected) {
        }
        // Non-reserved ALL-CAPS names still register fine (that namespace belongs to the host).
        b.register("ANSWER", NOOP);
        Assert.assertNotNull(b.get("ANSWER"));
        b.shutdown();
    }

    private static BuiltinFunctions newBuiltins() {
        BuiltinFunctions.PrintFunction sink = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] args) {
            }
        };
        return new BuiltinFunctions(sink, sink);
    }
}
