package com.flatide.tests;

import com.flatide.core.ScriptParser;
import com.flatide.interpreter.BuiltinFunctions;
import com.flatide.interpreter.ProperTeeInterpreter;
import com.flatide.parser.ProperTeeParser;
import com.flatide.platform.DefaultPlatformProvider;
import com.flatide.scheduler.Scheduler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Verifies the HTTP_GET / HTTP_POST / HTTP builtins against an embedded HttpServer (no external
 * network). A completed request yields {@code value = {status, body, headers}} with {@code ok}
 * true only for 2xx; a transport failure yields {@code ok=false, value.status=0}.
 */
public class HttpBuiltinTest {

    @Test
    public void httpBuiltinsCoverGetPostStatusAndTransportFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", new HttpHandler() {
            public void handle(HttpExchange e) throws java.io.IOException {
                byte[] b = "hello-body".getBytes("UTF-8");
                e.sendResponseHeaders(200, b.length);
                OutputStream o = e.getResponseBody();
                o.write(b);
                o.close();
            }
        });
        server.createContext("/echo", new HttpHandler() {
            public void handle(HttpExchange e) throws java.io.IOException {
                InputStream in = e.getRequestBody();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] c = new byte[1024];
                int r;
                while ((r = in.read(c)) != -1) bo.write(c, 0, r);
                byte[] b = ("got:" + bo.toString("UTF-8")).getBytes("UTF-8");
                e.sendResponseHeaders(201, b.length);
                OutputStream o = e.getResponseBody();
                o.write(b);
                o.close();
            }
        });
        server.createContext("/notfound", new HttpHandler() {
            public void handle(HttpExchange e) throws java.io.IOException {
                byte[] b = "nope".getBytes("UTF-8");
                e.sendResponseHeaders(404, b.length);
                OutputStream o = e.getResponseBody();
                o.write(b);
                o.close();
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;

        final List<String> out = new ArrayList<String>();
        BuiltinFunctions.PrintFunction sink = new BuiltinFunctions.PrintFunction() {
            public void print(Object[] args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(args[i]);
                }
                out.add(sb.toString());
            }
        };

        String script =
            "g = HTTP_GET(\"" + base + "/hello\")\n" +
            "PRINT(\"GET\", g.ok, g.value.status, g.value.body)\n" +
            "p = HTTP_POST(\"" + base + "/echo\", \"payload42\")\n" +
            "PRINT(\"POST\", p.ok, p.value.status, p.value.body)\n" +
            "nf = HTTP_GET(\"" + base + "/notfound\")\n" +
            "PRINT(\"NF\", nf.ok, nf.value.status)\n" +
            "x = HTTP(\"GET\", \"" + base + "/hello\")\n" +
            "PRINT(\"GEN\", x.ok, x.value.status)\n" +
            "bad = HTTP_GET(\"http://127.0.0.1:1/nope\")\n" +
            "PRINT(\"BAD\", bad.ok, bad.value.status, LEN(bad.value.body) > 0)\n";

        try {
            List<String> errors = new ArrayList<String>();
            ProperTeeParser.RootContext tree = ScriptParser.parse(script, errors);
            Assert.assertNotNull("parse failed: " + errors, tree);
            BuiltinFunctions bf = new BuiltinFunctions(sink, sink, null, null, new DefaultPlatformProvider());
            ProperTeeInterpreter v = new ProperTeeInterpreter(
                new LinkedHashMap<String, Object>(), sink, sink, 1000, "error", bf);
            try {
                new Scheduler(v).run(v.createRootStepper(tree));
            } finally {
                bf.shutdown();
            }
        } finally {
            server.stop(0);
        }

        Assert.assertEquals("GET true 200 hello-body", out.get(0));
        Assert.assertEquals("POST true 201 got:payload42", out.get(1));
        Assert.assertEquals("NF false 404", out.get(2));
        Assert.assertEquals("GEN true 200", out.get(3));
        Assert.assertEquals("BAD false 0 true", out.get(4));
    }
}
