package com.propertee.stepper;

/**
 * Result from a single step of a Stepper.
 * BOUNDARY = statement boundary (like bare yield in JS)
 * COMMAND = scheduler command (SLEEP, SPAWN_THREADS)
 * DONE = stepper finished, result available via getResult()
 */
public class StepResult {
    public enum Type {
        BOUNDARY,
        COMMAND,
        DONE
    }

    private final Type type;
    private final Object value; // For DONE: the result; for COMMAND: the SchedulerCommand

    private StepResult(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static final StepResult BOUNDARY = new StepResult(Type.BOUNDARY, null);

    public static StepResult done(Object value) {
        return new StepResult(Type.DONE, value);
    }

    public static StepResult command(SchedulerCommand cmd) {
        return new StepResult(Type.COMMAND, cmd);
    }

    public Type getType() { return type; }
    public Object getValue() { return value; }

    public boolean isBoundary() { return type == Type.BOUNDARY; }
    public boolean isDone() { return type == Type.DONE; }
    public boolean isCommand() { return type == Type.COMMAND; }
}
