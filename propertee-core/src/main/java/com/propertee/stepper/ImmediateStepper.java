package com.propertee.stepper;

/**
 * A stepper that completes immediately with a single value.
 * Used for simple expression evaluations that don't need multiple steps.
 */
public class ImmediateStepper implements Stepper {
    private final Object value;
    private boolean done = false;

    public ImmediateStepper(Object value) {
        this.value = value;
    }

    @Override
    public StepResult step() {
        done = true;
        return StepResult.done(value);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public Object getResult() {
        return value;
    }

    @Override
    public void setSendValue(Object value) {
        // No-op for immediate steppers
    }
}
