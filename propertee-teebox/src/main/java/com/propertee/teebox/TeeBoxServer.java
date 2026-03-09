package com.propertee.teebox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.propertee.task.TaskInfo;
import com.propertee.task.TaskObservation;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class TeeBoxServer {
    private final TeeBoxConfig config;
    private final RunManager runManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final AdminPageRenderer pageRenderer;
    private final HttpServer server;

    public TeeBoxServer(TeeBoxConfig config) throws IOException {
        this.config = config;
        this.runManager = new RunManager(config.scriptsRoot, config.dataDir, config.maxConcurrentRuns);
        this.pageRenderer = new AdminPageRenderer(config, runManager, gson);
        this.server = HttpServer.create(new InetSocketAddress(config.bindAddress, config.port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        registerContexts();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        runManager.shutdown();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void registerContexts() {
        server.createContext("/api", new ApiHandler());
        server.createContext("/admin", new AdminHandler());
        server.createContext("/", new RootHandler());
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            redirect(exchange, "/admin");
        }
    }

    private class AdminHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            try {
                if ("GET".equals(method) && "/admin".equals(path)) {
                    writeHtml(exchange, HttpURLConnection.HTTP_OK, pageRenderer.renderIndexPage());
                    return;
                }
                if ("POST".equals(method) && "/admin/submit".equals(path)) {
                    Map<String, String> form = parseForm(exchange);
                    RunRequest request = new RunRequest();
                    request.scriptPath = form.get("scriptPath");
                    request.props = parsePropsJson(form.get("propsJson"));
                    request.maxIterations = parseInt(form.get("maxIterations"), 1000);
                    request.warnLoops = "on".equals(form.get("warnLoops")) || "true".equals(form.get("warnLoops"));
                    RunInfo run = runManager.submit(request);
                    redirect(exchange, "/admin/runs/" + urlPath(run.runId));
                    return;
                }
                if ("GET".equals(method) && path.startsWith("/admin/runs/")) {
                    String suffix = path.substring("/admin/runs/".length());
                    if (suffix.endsWith("/kill-tasks")) {
                        writeText(exchange, HttpURLConnection.HTTP_BAD_METHOD, "Use POST");
                        return;
                    }
                    writeHtml(exchange, HttpURLConnection.HTTP_OK, pageRenderer.renderRunPage(suffix));
                    return;
                }
                if ("POST".equals(method) && path.startsWith("/admin/runs/") && path.endsWith("/kill-tasks")) {
                    String runId = path.substring("/admin/runs/".length(), path.length() - "/kill-tasks".length());
                    runManager.killRunTasks(runId);
                    redirect(exchange, "/admin/runs/" + urlPath(runId));
                    return;
                }
                if ("GET".equals(method) && path.startsWith("/admin/tasks/")) {
                    String suffix = path.substring("/admin/tasks/".length());
                    if (suffix.endsWith("/kill")) {
                        writeText(exchange, HttpURLConnection.HTTP_BAD_METHOD, "Use POST");
                        return;
                    }
                    writeHtml(exchange, HttpURLConnection.HTTP_OK, pageRenderer.renderTaskPage(suffix));
                    return;
                }
                if ("POST".equals(method) && path.startsWith("/admin/tasks/") && path.endsWith("/kill")) {
                    String taskId = path.substring("/admin/tasks/".length(), path.length() - "/kill".length());
                    TaskInfo info = runManager.getTask(taskId);
                    runManager.killTask(taskId);
                    if (info != null && info.runId != null) {
                        redirect(exchange, "/admin/runs/" + urlPath(info.runId));
                    } else {
                        redirect(exchange, "/admin/tasks/" + urlPath(taskId));
                    }
                    return;
                }
                writeText(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Not found");
            } catch (IllegalArgumentException e) {
                writeHtml(exchange, HttpURLConnection.HTTP_BAD_REQUEST, pageRenderer.renderErrorPage("Bad request", e.getMessage()));
            } catch (Exception e) {
                writeHtml(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, pageRenderer.renderErrorPage("Server error", e.getMessage()));
            }
        }
    }

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            try {
                if (!isAuthorized(exchange)) {
                    writeJson(exchange, HttpURLConnection.HTTP_UNAUTHORIZED, errorMap("Unauthorized"));
                    return;
                }
                if ("GET".equals(method) && "/api/runs".equals(path)) {
                    Map<String, String> query = parseQuery(exchange);
                    String status = trimToNull(query.get("status"));
                    int offset = parseInt(query.get("offset"), 0);
                    int limit = parseInt(query.get("limit"), -1);
                    writeJson(exchange, HttpURLConnection.HTTP_OK, runManager.listRuns(status, offset, limit));
                    return;
                }
                if ("POST".equals(method) && "/api/runs".equals(path)) {
                    RunRequest request = parseRunRequest(exchange);
                    RunInfo run = runManager.submit(request);
                    writeJson(exchange, HttpURLConnection.HTTP_ACCEPTED, run);
                    return;
                }
                if ("GET".equals(method) && path.startsWith("/api/runs/")) {
                    String suffix = path.substring("/api/runs/".length());
                    if (suffix.endsWith("/threads")) {
                        String runId = suffix.substring(0, suffix.length() - "/threads".length());
                        writeJson(exchange, HttpURLConnection.HTTP_OK, runManager.listThreads(runId));
                        return;
                    }
                    if (suffix.endsWith("/tasks")) {
                        String runId = suffix.substring(0, suffix.length() - "/tasks".length());
                        writeJson(exchange, HttpURLConnection.HTTP_OK, runManager.listTasksForRun(runId));
                        return;
                    }
                    Map<String, Object> detail = buildRunDetailMap(suffix);
                    if (detail == null) {
                        writeJson(exchange, HttpURLConnection.HTTP_NOT_FOUND, errorMap("Run not found"));
                        return;
                    }
                    writeJson(exchange, HttpURLConnection.HTTP_OK, detail);
                    return;
                }
                if ("POST".equals(method) && path.startsWith("/api/runs/") && path.endsWith("/kill-tasks")) {
                    String runId = path.substring("/api/runs/".length(), path.length() - "/kill-tasks".length());
                    Map<String, Object> result = new LinkedHashMap<String, Object>();
                    result.put("runId", runId);
                    result.put("killed", Integer.valueOf(runManager.killRunTasks(runId)));
                    writeJson(exchange, HttpURLConnection.HTTP_OK, result);
                    return;
                }
                if ("GET".equals(method) && "/api/tasks".equals(path)) {
                    Map<String, String> query = parseQuery(exchange);
                    String runId = trimToNull(query.get("runId"));
                    String status = trimToNull(query.get("status"));
                    int offset = parseInt(query.get("offset"), 0);
                    int limit = parseInt(query.get("limit"), -1);
                    Map<String, Object> payload = new LinkedHashMap<String, Object>();
                    payload.put("tasks", runManager.listTasks(runId, status, offset, limit));
                    if (runId == null && status == null && offset == 0 && limit <= 0) {
                        payload.put("detached", runManager.listDetachedTasks());
                    } else {
                        payload.put("detached", new ArrayList<TaskInfo>());
                    }
                    writeJson(exchange, HttpURLConnection.HTTP_OK, payload);
                    return;
                }
                if ("GET".equals(method) && path.startsWith("/api/tasks/")) {
                    String taskId = path.substring("/api/tasks/".length());
                    if (taskId.endsWith("/kill")) {
                        writeJson(exchange, HttpURLConnection.HTTP_BAD_METHOD, errorMap("Use POST"));
                        return;
                    }
                    Map<String, Object> detail = buildTaskDetailMap(taskId);
                    if (detail == null) {
                        writeJson(exchange, HttpURLConnection.HTTP_NOT_FOUND, errorMap("Task not found"));
                        return;
                    }
                    writeJson(exchange, HttpURLConnection.HTTP_OK, detail);
                    return;
                }
                if ("POST".equals(method) && path.startsWith("/api/tasks/") && path.endsWith("/kill")) {
                    String taskId = path.substring("/api/tasks/".length(), path.length() - "/kill".length());
                    Map<String, Object> result = new LinkedHashMap<String, Object>();
                    result.put("taskId", taskId);
                    result.put("killed", Boolean.valueOf(runManager.killTask(taskId)));
                    writeJson(exchange, HttpURLConnection.HTTP_OK, result);
                    return;
                }
                writeJson(exchange, HttpURLConnection.HTTP_NOT_FOUND, errorMap("Not found"));
            } catch (IllegalArgumentException e) {
                writeJson(exchange, HttpURLConnection.HTTP_BAD_REQUEST, errorMap(e.getMessage()));
            } catch (Exception e) {
                writeJson(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, errorMap(e.getMessage()));
            }
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        if (config.apiToken == null || config.apiToken.length() == 0) {
            return true;
        }
        Headers headers = exchange.getRequestHeaders();
        String auth = headers.getFirst("Authorization");
        return ("Bearer " + config.apiToken).equals(auth);
    }

    private RunRequest parseRunRequest(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body == null || body.trim().length() == 0) {
            throw new IllegalArgumentException("Request body is required");
        }
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> raw = gson.fromJson(body, type);
        if (raw == null) {
            throw new IllegalArgumentException("Invalid JSON body");
        }
        RunRequest request = new RunRequest();
        Object scriptPath = raw.get("scriptPath");
        request.scriptPath = scriptPath instanceof String ? ((String) scriptPath).trim() : null;
        Object props = raw.get("props");
        if (props instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propsMap = (Map<String, Object>) props;
            request.props = normalizeNumbers(propsMap);
        }
        Object maxIterations = raw.get("maxIterations");
        if (maxIterations instanceof Number) {
            request.maxIterations = ((Number) maxIterations).intValue();
        }
        Object warnLoops = raw.get("warnLoops");
        if (warnLoops instanceof Boolean) {
            request.warnLoops = ((Boolean) warnLoops).booleanValue();
        }
        return request;
    }

    private Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (body == null || body.length() == 0) {
            return result;
        }
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            if (pair.length() == 0) continue;
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            result.put(urlDecode(key), urlDecode(value));
        }
        return result;
    }

    private Map<String, String> parseQuery(HttpExchange exchange) throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.length() == 0) {
            return result;
        }
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            if (pair.length() == 0) continue;
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            result.put(urlDecode(key), urlDecode(value));
        }
        return result;
    }

    private Map<String, Object> parsePropsJson(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return new LinkedHashMap<String, Object>();
        }
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> props = gson.fromJson(raw, type);
        if (props == null) {
            return new LinkedHashMap<String, Object>();
        }
        return normalizeNumbers(props);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeNumbers(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Double) {
                double d = ((Double) value).doubleValue();
                if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                    normalized.put(entry.getKey(), Integer.valueOf((int) d));
                } else {
                    normalized.put(entry.getKey(), value);
                }
            } else if (value instanceof Map) {
                normalized.put(entry.getKey(), normalizeNumbers((Map<String, Object>) value));
            } else if (value instanceof List) {
                normalized.put(entry.getKey(), normalizeList((List<Object>) value));
            } else {
                normalized.put(entry.getKey(), value);
            }
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private List<Object> normalizeList(List<Object> list) {
        List<Object> normalized = new ArrayList<Object>();
        for (Object value : list) {
            if (value instanceof Double) {
                double d = ((Double) value).doubleValue();
                if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                    normalized.add(Integer.valueOf((int) d));
                } else {
                    normalized.add(value);
                }
            } else if (value instanceof Map) {
                normalized.add(normalizeNumbers((Map<String, Object>) value));
            } else if (value instanceof List) {
                normalized.add(normalizeList((List<Object>) value));
            } else {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private Map<String, Object> buildRunDetailMap(String runId) {
        RunInfo run = runManager.getRun(runId);
        if (run == null) {
            return null;
        }
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("run", run);
        detail.put("threads", runManager.listThreads(runId));
        detail.put("tasks", runManager.listTasksForRun(runId));
        return detail;
    }

    private Map<String, Object> buildTaskDetailMap(String taskId) {
        TaskInfo info = runManager.getTask(taskId);
        if (info == null) {
            return null;
        }
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("task", info);
        detail.put("observation", runManager.observeTask(taskId));
        detail.put("stdoutTail", tail(runManager.getTaskStdout(taskId), 4000));
        detail.put("stderrTail", tail(runManager.getTaskStderr(taskId), 4000));
        return detail;
    }

    private void writeJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = gson.toJson(payload).getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream out = exchange.getResponseBody();
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
    }

    private void writeHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream out = exchange.getResponseBody();
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
    }

    private void writeText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream out = exchange.getResponseBody();
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream input = exchange.getRequestBody();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = input.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toString("UTF-8");
        } finally {
            input.close();
        }
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("error", message);
        return map;
    }

    private String tail(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(text.length() - maxChars);
    }

    private int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.trim().length() == 0) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    private String urlDecode(String value) throws IOException {
        return URLDecoder.decode(value, "UTF-8");
    }

    private String urlPath(String value) {
        if (value == null) return "";
        return value.replace(" ", "%20");
    }
}
