package com.flatide.propertee.scheduler;

public enum ThreadState {
    READY,
    RUNNING,
    SLEEPING,
    WAITING,
    BLOCKED,
    COMPLETED,
    ERROR
}
