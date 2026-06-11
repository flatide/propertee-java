package com.flatide.tests;

import com.flatide.interpreter.BuiltinFunctions;
import com.flatide.task.TaskRequest;
import com.flatide.task.UnsupportedTaskRunner;

import org.junit.Assert;
import org.junit.Test;

public class UnsupportedTaskRunnerTest {

    @Test(expected = UnsupportedOperationException.class)
    public void unsupportedRunnerShouldRejectExecute() {
        UnsupportedTaskRunner runner = new UnsupportedTaskRunner();
        TaskRequest request = new TaskRequest();
        request.command = "echo test";
        runner.execute(request);
    }

    @Test
    public void builtinsShouldDefaultToUnsupportedRunnerWithoutHost() {
        BuiltinFunctions builtins = new BuiltinFunctions(noopPrint(), noopPrint());
        Assert.assertTrue(builtins.getTaskRunner() instanceof UnsupportedTaskRunner);
    }

    private static BuiltinFunctions.PrintFunction noopPrint() {
        return new BuiltinFunctions.PrintFunction() {
            @Override
            public void print(Object[] args) {
                // no-op
            }
        };
    }
}
