package com.flatide.stepper;

/**
 * Yields a single {@link SchedulerCommand} on the first step (so the scheduler can act on it, e.g.
 * put the thread to sleep), then completes with a fixed value.
 *
 * <p>Used when a builtin spawned directly as a thread body — e.g. {@code thread a: SLEEP(500)} —
 * returns a {@code SchedulerCommand} rather than a plain value. The command must reach the scheduler
 * (so the worker actually sleeps) instead of being wrapped as the thread's immediate result.
 */
public class CommandThenDoneStepper implements Stepper {
    private final SchedulerCommand command;
    private final Object finalValue;
    private boolean commandYielded = false;
    private boolean done = false;

    public CommandThenDoneStepper(SchedulerCommand command, Object finalValue) {
        this.command = command;
        this.finalValue = finalValue;
    }

    @Override
    public StepResult step() {
        if (done) return StepResult.done(finalValue);
        if (!commandYielded) {
            commandYielded = true;
            return StepResult.command(command);
        }
        done = true;
        return StepResult.done(finalValue);
    }

    @Override
    public boolean isDone() { return done; }

    @Override
    public Object getResult() { return finalValue; }

    @Override
    public void setSendValue(Object value) { /* no-op */ }
}
