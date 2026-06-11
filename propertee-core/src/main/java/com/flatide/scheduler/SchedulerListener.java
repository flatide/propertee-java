package com.flatide.scheduler;

public interface SchedulerListener {
    void onThreadCreated(ThreadContext thread);
    void onThreadUpdated(ThreadContext thread);
    void onThreadCompleted(ThreadContext thread);
    void onThreadError(ThreadContext thread);
}
