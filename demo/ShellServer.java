import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;

/**
 * Standalone HTTP server that executes shell commands.
 * LOCAL-ONLY DEMO — not for production use.
 *
 * Endpoints:
 *   GET /ping         — health check
 *   GET /exec?cmd=... — execute shell command
 *   GET /stop         — graceful shutdown
 *
 * Usage: java ShellServer [port]   (default 8099)
 */
public class ShellServer {

    private static HttpServer server;

    public static void main(String[] args) throws Exception {
        int port = 8099;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/ping", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                respond(ex, 200, "{\"ok\": true, \"value\": \"pong\"}");
            }
        });

        server.createContext("/exec", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                String query = ex.getRequestURI().getQuery();
                if (query == null || !query.startsWith("cmd=")) {
                    respond(ex, 400, "{\"ok\": false, \"value\": \"Missing cmd parameter\"}");
                    return;
                }

                String cmd = URLDecoder.decode(query.substring(4), "UTF-8");

                try {
                    ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();

                    InputStream is = proc.getInputStream();
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] tmp = new byte[4096];
                    int n;
                    while ((n = is.read(tmp)) != -1) {
                        buf.write(tmp, 0, n);
                    }
                    int exitCode = proc.waitFor();
                    String output = buf.toString("UTF-8");

                    // Trim trailing newline
                    if (output.endsWith("\n")) {
                        output = output.substring(0, output.length() - 1);
                    }

                    String json = "{\"ok\": " + (exitCode == 0 ? "true" : "false")
                            + ", \"value\": " + escapeJson(output)
                            + ", \"exit\": " + exitCode + "}";
                    respond(ex, 200, json);
                } catch (Exception e) {
                    String json = "{\"ok\": false, \"value\": " + escapeJson(e.getMessage()) + ", \"exit\": -1}";
                    respond(ex, 500, json);
                }
            }
        });

        server.createContext("/stop", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                respond(ex, 200, "{\"ok\": true, \"value\": \"stopping\"}");
                server.stop(1);
            }
        });

        server.start();
        System.out.println("ShellServer running on port " + port);
        System.out.println("WARNING: This server executes arbitrary shell commands. Local use only.");
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:   sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
