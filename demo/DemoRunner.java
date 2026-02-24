import com.propertee.cli.Main;
import com.propertee.interpreter.ProperTeeInterpreter;
import com.propertee.interpreter.BuiltinFunctions;
import com.propertee.scheduler.Scheduler;
import com.propertee.stepper.Stepper;
import com.propertee.parser.ProperTeeParser;
import com.propertee.runtime.Result;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a ProperTee script with SHELL() and HTTP_GET() async external functions.
 * Requires ShellServer running and propertee-java.jar on the classpath.
 *
 * Usage: java -cp ../build/libs/propertee-java.jar:. DemoRunner [-s http://host:port] script.pt
 */
public class DemoRunner {

    private static String serverUrl = "http://localhost:8099";

    public static void main(String[] args) throws Exception {
        // Parse CLI args
        String scriptPath = null;
        for (int i = 0; i < args.length; i++) {
            if ("-s".equals(args[i]) && i + 1 < args.length) {
                serverUrl = args[++i];
            } else {
                scriptPath = args[i];
            }
        }

        if (scriptPath == null) {
            System.err.println("Usage: DemoRunner [-s http://host:port] script.pt");
            System.exit(1);
        }

        // Read script file
        String scriptText = readFile(scriptPath);

        // Parse
        List<String> errors = new ArrayList<String>();
        ProperTeeParser.RootContext tree = Main.parseScript(scriptText, errors);
        if (tree == null) {
            for (String err : errors) {
                System.err.println("Parse error: " + err);
            }
            System.exit(1);
        }

        // Create interpreter with stdout/stderr handlers
        BuiltinFunctions.PrintFunction stdout = new BuiltinFunctions.PrintFunction() {
            public void print(Object[] args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(String.valueOf(args[i]));
                }
                System.out.println(sb.toString());
            }
        };
        BuiltinFunctions.PrintFunction stderr = new BuiltinFunctions.PrintFunction() {
            public void print(Object[] args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(String.valueOf(args[i]));
                }
                System.err.println(sb.toString());
            }
        };

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        ProperTeeInterpreter visitor = new ProperTeeInterpreter(properties, stdout, stderr, 1000, "error");

        // Register async external functions
        registerShellFunction(visitor);
        registerHttpGetFunction(visitor);

        // Run
        Scheduler scheduler = new Scheduler(visitor);
        Stepper mainStepper = visitor.createRootStepper(tree);
        try {
            scheduler.run(mainStepper);
        } catch (Exception e) {
            System.err.println("Runtime error: " + e.getMessage());
            System.exit(1);
        } finally {
            visitor.builtins.shutdown();
        }
    }

    private static void registerShellFunction(ProperTeeInterpreter visitor) {
        visitor.builtins.registerExternalAsync("SHELL", new BuiltinFunctions.BuiltinFunction() {
            public Object call(List<Object> args) {
                if (args.isEmpty()) {
                    throw new RuntimeException("SHELL() requires a command argument");
                }
                String cmd = String.valueOf(args.get(0));
                try {
                    String encoded = URLEncoder.encode(cmd, "UTF-8");
                    String json = httpGet(serverUrl + "/exec?cmd=" + encoded);
                    JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
                    boolean ok = obj.get("ok").getAsBoolean();
                    String value = obj.get("value").getAsString();
                    if (ok) {
                        return Result.ok(value);
                    } else {
                        return Result.error("Command failed (exit " + obj.get("exit").getAsInt() + "): " + value);
                    }
                } catch (Exception e) {
                    return Result.error("SHELL error: " + e.getMessage());
                }
            }
        });
    }

    private static void registerHttpGetFunction(ProperTeeInterpreter visitor) {
        visitor.builtins.registerExternalAsync("HTTP_GET", new BuiltinFunctions.BuiltinFunction() {
            public Object call(List<Object> args) {
                if (args.isEmpty()) {
                    return Result.error("HTTP_GET() requires a URL argument");
                }
                String url = String.valueOf(args.get(0));
                try {
                    return Result.ok(httpGet(url));
                } catch (Exception e) {
                    return Result.error("HTTP_GET error: " + e.getMessage());
                }
            }
        });
    }

    private static String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try {
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = is.read(tmp)) != -1) {
                buf.write(tmp, 0, n);
            }
            return buf.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private static String readFile(String path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }
}
