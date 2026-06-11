package com.flatide.runtime;

public class BreakException extends RuntimeException {
    public BreakException() {
        super("break");
    }
}
