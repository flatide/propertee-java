package com.propertee.tests;

import com.propertee.interpreter.BuiltinFunctions;
import com.propertee.task.TaskRequest;
import com.propertee.task.UnsupportedTaskRunner;

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
