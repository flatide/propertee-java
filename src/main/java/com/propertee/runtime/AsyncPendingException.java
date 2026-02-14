package com.propertee.runtime;

/**
 * Thrown by async external function wrappers to unwind expression evaluation.
 * Not a real error — used as control flow to signal that an async operation
 * has been submitted and the statement should be retried after the future completes.
 */
public class AsyncPendingException extends RuntimeException {
    public AsyncPendingException() {
        super();
    }
}
