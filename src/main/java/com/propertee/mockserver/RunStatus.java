package com.propertee.mockserver;

public enum RunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCEL_REQUESTED,
    SERVER_RESTARTED
}
