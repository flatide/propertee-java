package com.propertee.scheduler;

import com.propertee.interpreter.ScopeStack;
import com.propertee.stepper.Stepper;

import java.util.*;

public class ThreadContext {
    public int id;
    public String name;
    public Stepper stepper;
    public ThreadState state = ThreadState.READY;

    // Scope stack - thread-private local variables
    public ScopeStack scopeStack = new ScopeStack();

    // Global snapshot (read-only for threads)
    public Map<String, Object> globalSnapshot;

    // Sleep tracking
    public Long sleepUntil = null;

    // Context flags
    public boolean inThreadContext = false;
    public boolean inMonitorContext = false;
    public boolean inMultiContext = false;

    // Multi result vars
    public Map<String, Object> multiResultVars = new LinkedHashMap<String, Object>();

    // Thread result
    public Object result = null;

    // Error
    public Throwable error = null;

    // Parent thread id
    public Integer parentId = null;

    // Child thread tracking
    public Set<Integer> waitingForChildren = null;

    // Scheduler bookkeeping for MULTI
    public Map<String, Object> resultCollection = null; // live collection updated in-place
    public List<Integer> childIds = null;
    public List<String> resultKeyNames = null;
    public String resultCollectionVarName = null; // the [result] variable name
    public Object collectedResults = null;

    // Per-child tracking
    public String resultKeyName = null; // this child's key in the collection
    public Map<String, Object> localScope = null;

    // Async external function support
    public Map<String, Object> asyncResultCache = new LinkedHashMap<String, Object>();
    public java.util.concurrent.Future<Object> asyncFuture = null;
    public String asyncCacheKey = null;
    public long asyncTimeoutMs = 0;
    public long asyncSubmitTime = 0;

    public ThreadContext(int id, String name, Stepper stepper, Map<String, Object> globalSnapshot) {
        this.id = id;
        this.name = name;
        this.stepper = stepper;
        this.globalSnapshot = globalSnapshot;
    }

    public void markRunning() { state = ThreadState.RUNNING; }
    public void markReady() { state = ThreadState.READY; }

    public void markSleeping(long until) {
        state = ThreadState.SLEEPING;
        sleepUntil = until;
    }

    public void markWaiting(List<Integer> childIds) {
        state = ThreadState.WAITING;
        waitingForChildren = new HashSet<Integer>(childIds);
    }

    public void markCompleted(Object result) {
        state = ThreadState.COMPLETED;
        this.result = result;
    }

    public void markError(Throwable error) {
        state = ThreadState.ERROR;
        this.error = error;
    }

    public void markBlocked() { state = ThreadState.BLOCKED; }

    public void clearAsyncState() {
        asyncResultCache.clear();
        asyncFuture = null;
        asyncCacheKey = null;
        asyncTimeoutMs = 0;
        asyncSubmitTime = 0;
    }

    public boolean shouldWake(long now) {
        return state == ThreadState.SLEEPING && sleepUntil != null && now >= sleepUntil;
    }

    public boolean childCompleted(int childId) {
        if (waitingForChildren != null) {
            waitingForChildren.remove(childId);
            if (waitingForChildren.isEmpty()) {
                state = ThreadState.READY;
                waitingForChildren = null;
                return true;
            }
        }
        return false;
    }
}
