package com.flatide.tests;

import com.flatide.core.ScriptParser;
import com.flatide.interpreter.BuiltinFunctions;
import com.flatide.interpreter.ProperTeeInterpreter;
import com.flatide.parser.ProperTeeParser;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Static validation pass (ProperTee issue #9): the runtime enforces host restrictions only when a
 * construct is reached; {@link ProperTeeInterpreter#validate} must report forbidden constructs in
 * untaken branches too, with the runtime's message text and the construct's position.
 */
public class ValidatorTest {

    private static ProperTeeInterpreter interpreter() {
        BuiltinFunctions.PrintFunction noop = new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] args) { /* discard */ }
        };
        return new ProperTeeInterpreter(
                new LinkedHashMap<String, Object>(), noop, noop, 1000, "error", null);
    }

    private static ProperTeeParser.RootContext parse(String script) {
        List<String> errors = new ArrayList<String>();
        ProperTeeParser.RootContext tree = ScriptParser.parse(script, errors);
        Assert.assertNotNull("parse failed: " + errors, tree);
        return tree;
    }

    /** The issue's motivating case: forbidden constructs sit in a branch the run never takes. */
    @Test
    public void reportsForbiddenConstructsInUntakenBranches() {
        String script =
            "mode = \"safe\"\n" +
            "if mode == \"safe\" then\n" +
            "    PRINT(\"ok\")\n" +
            "else\n" +
            "    multi r do\n" +
            "        thread : SHELL(\"rm -rf /\")\n" +
            "    end\n" +
            "end\n";
        ProperTeeInterpreter interp = interpreter();
        interp.setHiddenKeywords(new HashSet<String>(Arrays.asList("multi")));
        interp.setIgnoredFunctions(new HashSet<String>(Arrays.asList("SHELL")));
        Assert.assertEquals(Arrays.asList(
                "line 5:4: 'multi' is not available in this environment",
                "line 6:17: 'SHELL' is not available in this environment"),
            interp.validate(parse(script)));
    }

    @Test
    public void coversAllSixHideableKeywordsAndCleanScripts() {
        String script =
            "function w() do\n" +
            "    return 1\n" +
            "end\n" +
            "if true then PRINT(1) end\n" +
            "loop i in [1] do PRINT(i) end\n" +
            "debug\n" +
            "multi r do\n" +
            "    thread : w()\n" +
            "end\n";
        ProperTeeInterpreter interp = interpreter();
        interp.setHiddenKeywords(new HashSet<String>(
                Arrays.asList("if", "loop", "function", "multi", "thread", "debug")));
        Assert.assertEquals(Arrays.asList(
                "line 1:0: 'function' is not available in this environment",
                "line 4:0: 'if' is not available in this environment",
                "line 5:0: 'loop' is not available in this environment",
                "line 6:0: 'debug' is not available in this environment",
                "line 7:0: 'multi' is not available in this environment",
                "line 8:4: 'thread' is not available in this environment"),
            interp.validate(parse(script)));

        // Clean script / no restrictions -> empty.
        Assert.assertTrue(interp.validate(parse("x = 1\n")).isEmpty());
        Assert.assertTrue(interpreter().validate(parse(script)).isEmpty());
    }

    @Test
    public void hidingIfReportsAnElseifChainOnceAndEveryCallSiteIsReported() {
        ProperTeeInterpreter interp = interpreter();
        interp.setHiddenKeywords(new HashSet<String>(Arrays.asList("if")));
        Assert.assertEquals(Arrays.asList("line 2:0: 'if' is not available in this environment"),
            interp.validate(parse(
                "x = 2\nif x == 1 then PRINT(1)\nelseif x == 2 then PRINT(2)\nelse PRINT(3)\nend\n")));

        ProperTeeInterpreter shell = interpreter();
        shell.setIgnoredFunctions(new HashSet<String>(Arrays.asList("SHELL")));
        Assert.assertEquals(Arrays.asList(
                "line 1:0: 'SHELL' is not available in this environment",
                "line 3:0: 'SHELL' is not available in this environment"),
            shell.validate(parse("SHELL(\"a\")\nx = 1\nSHELL(\"b\")\n")));
    }
}
