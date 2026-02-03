package com.propertee.interpreter;

import com.propertee.parser.ProperTeeParser;
import java.util.List;

public class FunctionDef {
    private final String name;
    private final List<String> params;
    private final ProperTeeParser.BlockContext body;
    private final boolean isThread;

    public FunctionDef(String name, List<String> params, ProperTeeParser.BlockContext body, boolean isThread) {
        this.name = name;
        this.params = params;
        this.body = body;
        this.isThread = isThread;
    }

    public String getName() { return name; }
    public List<String> getParams() { return params; }
    public ProperTeeParser.BlockContext getBody() { return body; }
    public boolean isThread() { return isThread; }
}
