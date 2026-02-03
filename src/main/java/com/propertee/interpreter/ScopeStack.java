package com.propertee.interpreter;

import java.util.*;

public class ScopeStack {
    private final List<Map<String, Object>> stack = new ArrayList<Map<String, Object>>();

    public void push(Map<String, Object> scope) {
        stack.add(scope);
    }

    public Map<String, Object> pop() {
        if (stack.isEmpty()) return null;
        return stack.remove(stack.size() - 1);
    }

    public Map<String, Object> peek() {
        if (stack.isEmpty()) return null;
        return stack.get(stack.size() - 1);
    }

    public int size() {
        return stack.size();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    /** Look up a variable through the scope chain. Returns null sentinel if not found. */
    public Object get(String name) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            Map<String, Object> scope = stack.get(i);
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return UNDEFINED;
    }

    /** Check if a variable exists in any scope */
    public boolean has(String name) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).containsKey(name)) return true;
        }
        return false;
    }

    /** Set a variable in the top scope */
    public void set(String name, Object value) {
        if (!stack.isEmpty()) {
            stack.get(stack.size() - 1).put(name, value);
        }
    }

    /** Sentinel for undefined */
    public static final Object UNDEFINED = new Object() {
        @Override
        public String toString() {
            return "undefined";
        }
    };
}
