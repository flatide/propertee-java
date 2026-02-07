package com.propertee.interpreter;

import com.propertee.parser.ProperTeeParser;
import java.util.List;

public class FunctionDef {
    private final String name;
    private final List<String> params;
    private final ProperTeeParser.BlockContext body;

    public FunctionDef(String name, List<String> params, ProperTeeParser.BlockContext body) {
        this.name = name;
        this.params = params;
        this.body = body;
    }

    public String getName() { return name; }
    public List<String> getParams() { return params; }
    public ProperTeeParser.BlockContext getBody() { return body; }
}
