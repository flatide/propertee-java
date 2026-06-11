package com.flatide.runtime;

public class ContinueException extends RuntimeException {
    public ContinueException() {
        super("continue");
    }
}
