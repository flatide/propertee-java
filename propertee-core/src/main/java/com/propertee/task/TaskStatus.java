package com.propertee.task;

import com.google.gson.annotations.SerializedName;

public enum TaskStatus {
    @SerializedName("starting")
    STARTING("starting"),
    @SerializedName("running")
    RUNNING("running"),
    @SerializedName("completed")
    COMPLETED("completed"),
    @SerializedName("failed")
    FAILED("failed"),
    @SerializedName("killed")
    KILLED("killed"),
    @SerializedName("detached")
    DETACHED("detached"),
    @SerializedName("lost")
    LOST("lost");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isTransient() {
        return this == STARTING || this == RUNNING || this == DETACHED;
    }
}
