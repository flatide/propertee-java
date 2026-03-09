package com.propertee.runtime;

public class ReturnException extends RuntimeException {
    private final Object value;

    public ReturnException(Object value) {
        super("return");
        this.value = value;
    }

    public Object getValue() {
        return value;
    }
}
