package com.propertee.stepper;

/**
 * Core interface replacing JS generators.
 * Each visit* method returns a Stepper that can be stepped through.
 * Statement-level steppers yield BOUNDARY results.
 * Expression-level steppers typically complete in one step (ImmediateStepper)
 * or delegate to child steppers.
 */
public interface Stepper {
    /** Advance one step. */
    StepResult step();

    /** Whether the stepper has completed. */
    boolean isDone();

    /** The final result (only valid when isDone() is true). */
    Object getResult();

    /** Send a value back into the stepper (used by scheduler for MULTI results). */
    void setSendValue(Object value);
}
