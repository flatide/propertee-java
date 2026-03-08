package com.propertee.mockserver;

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

public class MockAdminServer {
    private final MockServerConfig config;
    private final RunManager runManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HttpServer server;

    public MockAdminServer(MockServerConfig config) throws IOException {
        this.config = config;
        this.runManager = new RunManager(config.scriptsRoot, config.dataDir, config.maxConcurrentRuns);
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
                    writeHtml(exchange, HttpURLConnection.HTTP_OK, renderIndexPage());
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
                    writeHtml(exchange, HttpURLConnection.HTTP_OK, renderRunPage(suffix));
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
                    writeHtml(exchange, HttpURLConnection.HTTP_OK, renderTaskPage(suffix));
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
                writeHtml(exchange, HttpURLConnection.HTTP_BAD_REQUEST, renderErrorPage("Bad request", e.getMessage()));
            } catch (Exception e) {
                writeHtml(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, renderErrorPage("Server error", e.getMessage()));
            }
        }
    }

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            try {
                if ("GET".equals(method) && "/api/runs".equals(path)) {
                    writeJson(exchange, HttpURLConnection.HTTP_OK, runManager.listRuns());
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
                    Map<String, Object> payload = new LinkedHashMap<String, Object>();
                    payload.put("tasks", runManager.listAllTasks());
                    payload.put("detached", runManager.listDetachedTasks());
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

    private String renderIndexPage() {
        List<RunInfo> runs = runManager.listRuns();
        List<TaskInfo> detached = runManager.listDetachedTasks();
        StringBuilder sb = new StringBuilder();
        sb.append(pageStart("ProperTee Mock Admin", true));
        sb.append("<h1>ProperTee Mock Admin</h1>");
        sb.append("<p>scriptsRoot: <code>").append(escape(config.scriptsRoot.getAbsolutePath())).append("</code></p>");
        sb.append("<p>dataDir: <code>").append(escape(config.dataDir.getAbsolutePath())).append("</code></p>");
        sb.append("<p>active runs: ").append(runManager.getActiveCount()).append(" | queued: ").append(runManager.getQueuedCount()).append("</p>");
        sb.append("<h2>Run Script</h2>");
        sb.append("<form method='post' action='/admin/submit'>");
        sb.append("<label>scriptPath <input type='text' name='scriptPath' style='width: 420px' placeholder='jobs/sample.pt'/></label><br/>");
        sb.append("<label>props JSON <input type='text' name='propsJson' style='width: 420px' value='{}'/></label><br/>");
        sb.append("<label>maxIterations <input type='text' name='maxIterations' value='1000'/></label>");
        sb.append("<label style='margin-left: 16px'><input type='checkbox' name='warnLoops'/> warnLoops</label>");
        sb.append("<div style='margin-top: 8px'><button type='submit'>Submit</button></div>");
        sb.append("</form>");
        sb.append("<p><a href='/api/runs'>JSON: /api/runs</a> | <a href='/api/tasks'>JSON: /api/tasks</a></p>");
        sb.append("<h2>Runs</h2>");
        sb.append("<table><thead><tr><th>runId</th><th>script</th><th>status</th><th>created</th><th>started</th><th>ended</th><th>threads</th><th>tasks</th></tr></thead><tbody>");
        for (RunInfo run : runs) {
            sb.append("<tr>");
            sb.append("<td><a href='/admin/runs/").append(urlPath(run.runId)).append("'>").append(escape(run.runId)).append("</a></td>");
            sb.append("<td>").append(escape(run.scriptPath)).append("</td>");
            sb.append("<td>").append(statusBadge(run.status != null ? run.status.name() : "UNKNOWN")).append("</td>");
            sb.append("<td>").append(escape(formatTime(run.createdAt))).append("</td>");
            sb.append("<td>").append(escape(formatTime(run.startedAt))).append("</td>");
            sb.append("<td>").append(escape(formatTime(run.endedAt))).append("</td>");
            sb.append("<td>").append(run.threads != null ? run.threads.size() : 0).append("</td>");
            sb.append("<td>").append(runManager.listTasksForRun(run.runId).size()).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        sb.append("<h2>Detached Tasks</h2>");
        sb.append(renderTaskTable(detached, false));
        sb.append(pageEnd());
        return sb.toString();
    }

    private String renderRunPage(String runId) {
        RunInfo run = runManager.getRun(runId);
        if (run == null) {
            return renderErrorPage("Run not found", runId);
        }
        List<RunThreadInfo> threads = runManager.listThreads(runId);
        List<TaskInfo> tasks = runManager.listTasksForRun(runId);
        StringBuilder sb = new StringBuilder();
        sb.append(pageStart("Run " + runId, true));
        sb.append("<p><a href='/admin'>Back</a> | <a href='/api/runs/").append(urlPath(runId)).append("'>JSON</a></p>");
        sb.append("<h1>Run ").append(escape(runId)).append("</h1>");
        sb.append("<p>script: <code>").append(escape(run.scriptPath)).append("</code></p>");
        sb.append("<p>status: ").append(statusBadge(run.status != null ? run.status.name() : "UNKNOWN")).append("</p>");
        sb.append("<p>created: ").append(escape(formatTime(run.createdAt))).append(" | started: ").append(escape(formatTime(run.startedAt))).append(" | ended: ").append(escape(formatTime(run.endedAt))).append("</p>");
        sb.append("<form method='post' action='/admin/runs/").append(urlPath(runId)).append("/kill-tasks'><button type='submit'>Kill All Run Tasks</button></form>");
        if (run.errorMessage != null && run.errorMessage.length() > 0) {
            sb.append("<h2>Error</h2><pre>").append(escape(run.errorMessage)).append("</pre>");
        }
        if (run.resultSummary != null && run.resultSummary.length() > 0) {
            sb.append("<h2>Result</h2><pre>").append(escape(run.resultSummary)).append("</pre>");
        }
        sb.append("<h2>Threads</h2>");
        sb.append(renderThreadTable(threads));
        sb.append("<h2>Tasks / Processes</h2>");
        sb.append(renderTaskTable(tasks, true));
        sb.append("<h2>Stdout</h2><pre>").append(escape(joinLines(run.stdoutLines))).append("</pre>");
        sb.append("<h2>Stderr</h2><pre>").append(escape(joinLines(run.stderrLines))).append("</pre>");
        sb.append(pageEnd());
        return sb.toString();
    }

    private String renderTaskPage(String taskId) {
        TaskInfo info = runManager.getTask(taskId);
        if (info == null) {
            return renderErrorPage("Task not found", taskId);
        }
        TaskObservation obs = runManager.observeTask(taskId);
        StringBuilder sb = new StringBuilder();
        sb.append(pageStart("Task " + taskId, true));
        sb.append("<p><a href='/admin'>Back</a>");
        if (info.runId != null) {
            sb.append(" | <a href='/admin/runs/").append(urlPath(info.runId)).append("'>Run</a>");
        }
        sb.append(" | <a href='/api/tasks/").append(urlPath(taskId)).append("'>JSON</a></p>");
        sb.append("<h1>Task ").append(escape(taskId)).append("</h1>");
        sb.append("<p>status: ").append(statusBadge(info.status)).append("</p>");
        sb.append("<p>runId: ").append(escape(info.runId)).append(" | threadId: ").append(escape(info.threadId)).append(" | threadName: ").append(escape(info.threadName)).append("</p>");
        sb.append("<p>pid: ").append(info.pid).append(" | pgid: ").append(info.pgid).append(" | alive: ").append(info.alive).append("</p>");
        sb.append("<p>cwd: <code>").append(escape(info.cwd)).append("</code></p>");
        sb.append("<p>command:</p><pre>").append(escape(info.command)).append("</pre>");
        sb.append("<form method='post' action='/admin/tasks/").append(urlPath(taskId)).append("/kill'><button type='submit'>Kill Task</button></form>");
        if (obs != null) {
            sb.append("<h2>Observation</h2><pre>").append(escape(gson.toJson(obs))).append("</pre>");
        }
        sb.append("<h2>Stdout Tail</h2><pre>").append(escape(tail(runManager.getTaskStdout(taskId), 4000))).append("</pre>");
        sb.append("<h2>Stderr Tail</h2><pre>").append(escape(tail(runManager.getTaskStderr(taskId), 4000))).append("</pre>");
        sb.append(pageEnd());
        return sb.toString();
    }

    private String renderThreadTable(List<RunThreadInfo> threads) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr><th>id</th><th>name</th><th>state</th><th>parent</th><th>sleepUntil</th><th>async</th><th>resultKey</th><th>result</th><th>error</th><th>updated</th></tr></thead><tbody>");
        for (RunThreadInfo thread : threads) {
            sb.append("<tr>");
            sb.append("<td>").append(thread.threadId).append("</td>");
            sb.append("<td>").append(escape(thread.name)).append("</td>");
            sb.append("<td>").append(statusBadge(thread.state)).append("</td>");
            sb.append("<td>").append(escape(thread.parentId)).append("</td>");
            sb.append("<td>").append(escape(formatTime(thread.sleepUntil))).append("</td>");
            sb.append("<td>").append(thread.asyncPending).append("</td>");
            sb.append("<td>").append(escape(thread.resultKeyName)).append("</td>");
            sb.append("<td>").append(escape(thread.resultSummary)).append("</td>");
            sb.append("<td>").append(escape(thread.errorMessage)).append("</td>");
            sb.append("<td>").append(escape(formatTime(thread.updatedAt))).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String renderTaskTable(List<TaskInfo> tasks, boolean includeKill) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr><th>taskId</th><th>runId</th><th>thread</th><th>status</th><th>pid</th><th>pgid</th><th>alive</th><th>elapsedMs</th><th>lastOutputAgeMs</th><th>action</th></tr></thead><tbody>");
        for (TaskInfo task : tasks) {
            sb.append("<tr>");
            sb.append("<td><a href='/admin/tasks/").append(urlPath(task.taskId)).append("'>").append(escape(task.taskId)).append("</a></td>");
            sb.append("<td>").append(escape(task.runId)).append("</td>");
            sb.append("<td>").append(escape(task.threadName)).append(" (#").append(escape(task.threadId)).append(")</td>");
            sb.append("<td>").append(statusBadge(task.status)).append("</td>");
            sb.append("<td>").append(task.pid).append("</td>");
            sb.append("<td>").append(task.pgid).append("</td>");
            sb.append("<td>").append(task.alive).append("</td>");
            sb.append("<td>").append(task.elapsedMs).append("</td>");
            sb.append("<td>").append(escape(task.lastOutputAgeMs)).append("</td>");
            sb.append("<td>");
            if (includeKill) {
                sb.append("<form method='post' action='/admin/tasks/").append(urlPath(task.taskId)).append("/kill'><button type='submit'>Kill</button></form>");
            }
            sb.append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String renderErrorPage(String title, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(pageStart(title, false));
        sb.append("<p><a href='/admin'>Back</a></p>");
        sb.append("<h1>").append(escape(title)).append("</h1>");
        sb.append("<pre>").append(escape(message)).append("</pre>");
        sb.append(pageEnd());
        return sb.toString();
    }

    private String pageStart(String title, boolean autoRefresh) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'/>");
        if (autoRefresh) {
            sb.append("<meta http-equiv='refresh' content='2' />");
        }
        sb.append("<title>").append(escape(title)).append("</title>");
        sb.append("<style>");
        sb.append("body{font-family:Georgia,serif;margin:24px;background:#f7f3ec;color:#1f1f1f;} ");
        sb.append("table{border-collapse:collapse;width:100%;margin:12px 0;} ");
        sb.append("th,td{border:1px solid #c9bba7;padding:8px;vertical-align:top;font-size:14px;} ");
        sb.append("th{background:#e7dcc9;text-align:left;} ");
        sb.append("code,pre{background:#fffaf1;border:1px solid #d6c7b5;padding:8px;display:block;white-space:pre-wrap;} ");
        sb.append("input{padding:6px;} button{padding:6px 10px;background:#325c45;color:white;border:none;} ");
        sb.append(".badge{display:inline-block;padding:2px 8px;border-radius:999px;background:#d8ccb9;} ");
        sb.append("</style></head><body>");
        return sb.toString();
    }

    private String pageEnd() {
        return "</body></html>";
    }

    private String statusBadge(String status) {
        return "<span class='badge'>" + escape(status) + "</span>";
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

    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private String tail(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(text.length() - maxChars);
    }

    private String escape(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        text = text.replace("&", "&amp;");
        text = text.replace("<", "&lt;");
        text = text.replace(">", "&gt;");
        text = text.replace("\"", "&quot;");
        return text;
    }

    private String formatTime(Long epochMs) {
        if (epochMs == null) return "";
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        return format.format(new java.util.Date(epochMs.longValue()));
    }

    private String formatTime(long epochMs) {
        return formatTime(Long.valueOf(epochMs));
    }

    private int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.trim().length() == 0) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String urlDecode(String value) throws IOException {
        return URLDecoder.decode(value, "UTF-8");
    }

    private String urlPath(String value) {
        if (value == null) return "";
        return value.replace(" ", "%20");
    }
}
