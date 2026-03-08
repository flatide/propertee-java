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
        sb.append(pageStart("ProperTee Mock Admin"));
        sb.append("<div class='header'><h1>ProperTee Admin</h1>");
        sb.append("<div class='header-meta'>");
        sb.append("<span class='tag'>active ").append(runManager.getActiveCount()).append("</span> ");
        sb.append("<span class='tag'>queued ").append(runManager.getQueuedCount()).append("</span>");
        sb.append("</div></div>");

        sb.append("<div class='card'>");
        sb.append("<h2>Run Script</h2>");
        sb.append("<form method='post' action='/admin/submit' class='form-grid'>");
        sb.append("<div class='form-row'><label>Script Path</label><input type='text' name='scriptPath' placeholder='01_basic_run.pt'/></div>");
        sb.append("<div class='form-row'><label>Props (JSON)</label><input type='text' name='propsJson' value='{}'/></div>");
        sb.append("<div class='form-row-inline'>");
        sb.append("<div><label>Max Iterations</label><input type='text' name='maxIterations' value='1000' style='width:100px'/></div>");
        sb.append("<label class='checkbox-label'><input type='checkbox' name='warnLoops'/> Warn Loops</label>");
        sb.append("<button type='submit'>Run</button>");
        sb.append("</div></form></div>");

        sb.append("<div class='card'>");
        sb.append("<div class='card-header'><h2>Runs</h2>");
        sb.append("<div class='card-actions'><a href='/api/runs' class='link-subtle'>API</a> <a href='/api/tasks' class='link-subtle'>Tasks API</a></div></div>");
        if (runs.isEmpty()) {
            sb.append("<p class='empty'>No runs yet</p>");
        } else {
            sb.append("<div class='table-wrap'><table><thead><tr><th>Run ID</th><th>Script</th><th>Status</th><th>Created</th><th>Duration</th><th>Threads</th><th>Tasks</th></tr></thead><tbody>");
            for (RunInfo run : runs) {
                sb.append("<tr>");
                sb.append("<td><a href='/admin/runs/").append(urlPath(run.runId)).append("' class='mono'>").append(escape(run.runId)).append("</a></td>");
                sb.append("<td class='mono'>").append(escape(run.scriptPath)).append("</td>");
                sb.append("<td>").append(statusBadge(run.status != null ? run.status.name() : "UNKNOWN")).append("</td>");
                sb.append("<td class='dim'>").append(escape(formatTime(run.createdAt))).append("</td>");
                sb.append("<td class='dim'>").append(formatDuration(run.startedAt, run.endedAt)).append("</td>");
                sb.append("<td class='center'>").append(run.threads != null ? run.threads.size() : 0).append("</td>");
                sb.append("<td class='center'>").append(runManager.listTasksForRun(run.runId).size()).append("</td>");
                sb.append("</tr>");
            }
            sb.append("</tbody></table></div>");
        }
        sb.append("</div>");

        if (!detached.isEmpty()) {
            sb.append("<div class='card'>");
            sb.append("<h2>Detached Tasks</h2>");
            sb.append(renderTaskTable(detached, false));
            sb.append("</div>");
        }

        sb.append("<div class='footer'>");
        sb.append("<span class='dim'>scriptsRoot: ").append(escape(config.scriptsRoot.getAbsolutePath())).append("</span><br/>");
        sb.append("<span class='dim'>dataDir: ").append(escape(config.dataDir.getAbsolutePath())).append("</span>");
        sb.append("</div>");

        sb.append(pageEnd(true));
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
        sb.append(pageStart("Run " + runId));

        sb.append("<div class='nav'>");
        sb.append("<a href='/admin'>Dashboard</a>");
        sb.append("<span class='nav-sep'>/</span>");
        sb.append("<span>Run ").append(escape(shortId(runId))).append("</span>");
        sb.append("<span class='nav-sep'>|</span>");
        sb.append("<a href='/api/runs/").append(urlPath(runId)).append("' class='link-subtle'>JSON</a>");
        sb.append("</div>");

        sb.append("<div class='card'>");
        sb.append("<div class='card-header'><h2>").append(escape(runId)).append("</h2>");
        sb.append("<form method='post' action='/admin/runs/").append(urlPath(runId)).append("/kill-tasks'>");
        sb.append("<button type='submit' class='btn-danger btn-sm'>Kill All Tasks</button></form></div>");
        sb.append("<div class='detail-grid'>");
        sb.append("<div class='detail-item'><div class='detail-label'>Script</div><div class='detail-value'><code>").append(escape(run.scriptPath)).append("</code></div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>Status</div><div class='detail-value'>").append(statusBadge(run.status != null ? run.status.name() : "UNKNOWN")).append("</div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>Created</div><div class='detail-value dim'>").append(escape(formatTime(run.createdAt))).append("</div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>Started</div><div class='detail-value dim'>").append(escape(formatTime(run.startedAt))).append("</div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>Ended</div><div class='detail-value dim'>").append(escape(formatTime(run.endedAt))).append("</div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>Duration</div><div class='detail-value'>").append(formatDuration(run.startedAt, run.endedAt)).append("</div></div>");
        sb.append("</div></div>");

        if (run.errorMessage != null && run.errorMessage.length() > 0) {
            sb.append("<div class='card'><h2>Error</h2><pre>").append(escape(run.errorMessage)).append("</pre></div>");
        }
        if (run.resultSummary != null && run.resultSummary.length() > 0) {
            sb.append("<div class='card'><h2>Result</h2><pre>").append(escape(run.resultSummary)).append("</pre></div>");
        }

        sb.append("<div class='card'><h2>Threads (").append(threads.size()).append(")</h2>");
        sb.append(renderThreadTable(threads));
        sb.append("</div>");

        sb.append("<div class='card'><h2>Tasks (").append(tasks.size()).append(")</h2>");
        sb.append(renderTaskTable(tasks, true));
        sb.append("</div>");

        String stdout = joinLines(run.stdoutLines);
        String stderr = joinLines(run.stderrLines);
        if (stdout.length() > 0) {
            sb.append("<div class='card'><h2>Stdout</h2><pre>").append(escape(stdout)).append("</pre></div>");
        }
        if (stderr.length() > 0) {
            sb.append("<div class='card'><h2>Stderr</h2><pre>").append(escape(stderr)).append("</pre></div>");
        }

        sb.append(pageEnd(true));
        return sb.toString();
    }

    private String renderTaskPage(String taskId) {
        TaskInfo info = runManager.getTask(taskId);
        if (info == null) {
            return renderErrorPage("Task not found", taskId);
        }
        TaskObservation obs = runManager.observeTask(taskId);
        StringBuilder sb = new StringBuilder();
        sb.append(pageStart("Task " + taskId));

        sb.append("<div class='nav'>");
        sb.append("<a href='/admin'>Dashboard</a>");
        if (info.runId != null) {
            sb.append("<span class='nav-sep'>/</span>");
            sb.append("<a href='/admin/runs/").append(urlPath(info.runId)).append("'>Run ").append(escape(shortId(info.runId))).append("</a>");
        }
        sb.append("<span class='nav-sep'>/</span>");
        sb.append("<span>Task ").append(escape(shortId(taskId))).append("</span>");
        sb.append("<span class='nav-sep'>|</span>");
        sb.append("<a href='/api/tasks/").append(urlPath(taskId)).append("' class='link-subtle'>JSON</a>");
        sb.append("</div>");

        sb.append("<div class='card'>");
        sb.append("<div class='card-header'><h2>").append(escape(taskId)).append("</h2>");
        sb.append("<form method='post' action='/admin/tasks/").append(urlPath(taskId)).append("/kill'>");
        sb.append("<button type='submit' class='btn-danger btn-sm'>Kill Task</button></form></div>");
        sb.append("<div class='detail-grid'>");
        sb.append("<div class='detail-item'><div class='detail-label'>Status</div><div class='detail-value'>").append(statusBadge(info.status)).append("</div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>Run ID</div><div class='detail-value mono'>").append(escape(info.runId)).append("</div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>Thread</div><div class='detail-value'>").append(escape(info.threadName)).append(" <span class='dim'>#").append(escape(info.threadId)).append("</span></div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>PID</div><div class='detail-value mono'>").append(info.pid).append("</div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>PGID</div><div class='detail-value mono'>").append(info.pgid).append("</div></div>");
        sb.append("<div class='detail-item'><div class='detail-label'>Alive</div><div class='detail-value'>").append(info.alive ? statusBadge("RUNNING") : statusBadge("DONE")).append("</div></div>");
        sb.append("</div>");
        sb.append("<div class='detail-grid'>");
        sb.append("<div class='detail-item'><div class='detail-label'>CWD</div><div class='detail-value'><code>").append(escape(info.cwd)).append("</code></div></div>");
        sb.append("</div>");
        sb.append("<div class='detail-item' style='margin-top:12px'><div class='detail-label'>Command</div></div>");
        sb.append("<pre>").append(escape(info.command)).append("</pre>");
        sb.append("</div>");

        if (obs != null) {
            sb.append("<div class='card'><h2>Observation</h2><pre>").append(escape(gson.toJson(obs))).append("</pre></div>");
        }

        String stdoutTail = tail(runManager.getTaskStdout(taskId), 4000);
        String stderrTail = tail(runManager.getTaskStderr(taskId), 4000);
        if (stdoutTail.length() > 0) {
            sb.append("<div class='card'><h2>Stdout</h2><pre>").append(escape(stdoutTail)).append("</pre></div>");
        }
        if (stderrTail.length() > 0) {
            sb.append("<div class='card'><h2>Stderr</h2><pre>").append(escape(stderrTail)).append("</pre></div>");
        }

        sb.append(pageEnd(true));
        return sb.toString();
    }

    private String renderThreadTable(List<RunThreadInfo> threads) {
        if (threads.isEmpty()) {
            return "<p class='empty'>No threads</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='table-wrap'><table><thead><tr>");
        sb.append("<th>ID</th><th>Name</th><th>State</th><th>Parent</th><th>Key</th><th>Result</th><th>Error</th><th>Updated</th>");
        sb.append("</tr></thead><tbody>");
        for (RunThreadInfo thread : threads) {
            sb.append("<tr>");
            sb.append("<td class='center'>").append(thread.threadId).append("</td>");
            sb.append("<td class='mono'>").append(escape(thread.name)).append("</td>");
            sb.append("<td>").append(statusBadge(thread.state)).append("</td>");
            sb.append("<td class='center dim'>").append(escape(thread.parentId)).append("</td>");
            sb.append("<td class='mono'>").append(escape(thread.resultKeyName)).append("</td>");
            sb.append("<td>").append(escape(thread.resultSummary)).append("</td>");
            sb.append("<td>").append(escape(thread.errorMessage)).append("</td>");
            sb.append("<td class='dim'>").append(escape(formatTime(thread.updatedAt))).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String renderTaskTable(List<TaskInfo> tasks, boolean includeKill) {
        if (tasks.isEmpty()) {
            return "<p class='empty'>No tasks</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='table-wrap'><table><thead><tr>");
        sb.append("<th>Task ID</th><th>Thread</th><th>Status</th><th>PID</th><th>Alive</th><th>Elapsed</th>");
        if (includeKill) {
            sb.append("<th></th>");
        }
        sb.append("</tr></thead><tbody>");
        for (TaskInfo task : tasks) {
            sb.append("<tr>");
            sb.append("<td><a href='/admin/tasks/").append(urlPath(task.taskId)).append("' class='mono'>").append(escape(shortId(task.taskId))).append("</a></td>");
            sb.append("<td>").append(escape(task.threadName)).append(" <span class='dim'>#").append(escape(task.threadId)).append("</span></td>");
            sb.append("<td>").append(statusBadge(task.status)).append("</td>");
            sb.append("<td class='mono center'>").append(task.pid).append("</td>");
            sb.append("<td class='center'>").append(task.alive ? statusBadge("RUNNING") : "<span class='dim'>no</span>").append("</td>");
            sb.append("<td class='dim'>").append(formatElapsed(task.elapsedMs)).append("</td>");
            if (includeKill) {
                sb.append("<td>");
                if (task.alive) {
                    sb.append("<form method='post' action='/admin/tasks/").append(urlPath(task.taskId)).append("/kill'><button type='submit' class='btn-danger btn-sm'>Kill</button></form>");
                }
                sb.append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String renderErrorPage(String title, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(pageStart(title));
        sb.append("<div class='nav'><a href='/admin'>Dashboard</a><span class='nav-sep'>/</span><span>Error</span></div>");
        sb.append("<div class='card'>");
        sb.append("<h2>").append(escape(title)).append("</h2>");
        sb.append("<pre>").append(escape(message)).append("</pre>");
        sb.append("</div>");
        sb.append(pageEnd());
        return sb.toString();
    }

    private String pageStart(String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'/>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'/>");
        sb.append("<title>").append(escape(title)).append("</title>");
        sb.append("<style>");
        sb.append("*{box-sizing:border-box;margin:0;padding:0;} ");
        sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;");
        sb.append("background:#f0f2f5;color:#1a1a2e;line-height:1.5;padding:24px;max-width:1200px;margin:0 auto;} ");
        sb.append("a{color:#2563eb;text-decoration:none;} a:hover{text-decoration:underline;} ");
        sb.append("h1{font-size:22px;font-weight:600;} h2{font-size:16px;font-weight:600;margin:0 0 12px 0;} ");

        // header
        sb.append(".header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px;} ");
        sb.append(".header-meta{display:flex;gap:8px;} ");

        // card
        sb.append(".card{background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:20px;margin-bottom:16px;} ");
        sb.append(".card-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;} ");
        sb.append(".card-header h2{margin:0;} ");
        sb.append(".card-actions{display:flex;gap:12px;} ");

        // form
        sb.append(".form-grid{display:flex;flex-direction:column;gap:12px;} ");
        sb.append(".form-row{display:flex;flex-direction:column;gap:4px;} ");
        sb.append(".form-row label{font-size:13px;font-weight:500;color:#64748b;} ");
        sb.append(".form-row input{padding:8px 12px;border:1px solid #cbd5e1;border-radius:6px;font-size:14px;} ");
        sb.append(".form-row input:focus{outline:none;border-color:#2563eb;box-shadow:0 0 0 2px rgba(37,99,235,0.15);} ");
        sb.append(".form-row-inline{display:flex;align-items:flex-end;gap:16px;flex-wrap:wrap;} ");
        sb.append(".form-row-inline > div{display:flex;flex-direction:column;gap:4px;} ");
        sb.append(".form-row-inline label{font-size:13px;font-weight:500;color:#64748b;} ");
        sb.append(".form-row-inline input[type='text']{padding:8px 12px;border:1px solid #cbd5e1;border-radius:6px;font-size:14px;} ");
        sb.append(".checkbox-label{display:flex;align-items:center;gap:6px;font-size:14px;cursor:pointer;padding-bottom:4px;} ");

        // table
        sb.append(".table-wrap{overflow-x:auto;margin:0 -4px;} ");
        sb.append("table{border-collapse:collapse;width:100%;font-size:13px;} ");
        sb.append("th{background:#f8fafc;color:#64748b;font-weight:500;text-transform:uppercase;font-size:11px;letter-spacing:0.5px;");
        sb.append("padding:8px 12px;text-align:left;border-bottom:2px solid #e2e8f0;} ");
        sb.append("td{padding:8px 12px;border-bottom:1px solid #f1f5f9;vertical-align:top;} ");
        sb.append("tr:hover{background:#f8fafc;} ");

        // badge
        sb.append(".badge{display:inline-block;padding:2px 10px;border-radius:999px;font-size:11px;font-weight:600;letter-spacing:0.3px;} ");
        sb.append(".badge-running{background:#dbeafe;color:#1d4ed8;} ");
        sb.append(".badge-completed,.badge-done{background:#dcfce7;color:#15803d;} ");
        sb.append(".badge-error,.badge-failed{background:#fee2e2;color:#b91c1c;} ");
        sb.append(".badge-killed{background:#fef3c7;color:#92400e;} ");
        sb.append(".badge-queued,.badge-pending,.badge-waiting,.badge-blocked{background:#f3e8ff;color:#7c3aed;} ");
        sb.append(".badge-ready{background:#e0f2fe;color:#0369a1;} ");
        sb.append(".badge-sleeping{background:#fef9c3;color:#854d0e;} ");
        sb.append(".badge-lost{background:#fecaca;color:#991b1b;} ");

        // buttons
        sb.append("button,.btn{padding:8px 16px;background:#2563eb;color:#fff;border:none;border-radius:6px;");
        sb.append("font-size:13px;font-weight:500;cursor:pointer;transition:background 0.15s;} ");
        sb.append("button:hover,.btn:hover{background:#1d4ed8;} ");
        sb.append(".btn-danger{background:#dc2626;} .btn-danger:hover{background:#b91c1c;} ");
        sb.append(".btn-sm{padding:4px 10px;font-size:12px;} ");

        // utilities
        sb.append(".mono{font-family:'SF Mono',SFMono-Regular,Consolas,'Liberation Mono',Menlo,monospace;font-size:12px;} ");
        sb.append(".dim{color:#94a3b8;} ");
        sb.append(".center{text-align:center;} ");
        sb.append(".link-subtle{color:#64748b;font-size:12px;} .link-subtle:hover{color:#2563eb;} ");
        sb.append(".empty{color:#94a3b8;padding:24px 0;text-align:center;font-style:italic;} ");
        sb.append(".footer{margin-top:24px;padding-top:16px;border-top:1px solid #e2e8f0;font-size:12px;} ");
        sb.append(".tag{display:inline-block;padding:2px 10px;border-radius:999px;font-size:12px;font-weight:500;background:#e2e8f0;color:#475569;} ");

        // detail
        sb.append(".detail-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px;margin-bottom:16px;} ");
        sb.append(".detail-item{} .detail-item .detail-label{font-size:11px;color:#94a3b8;text-transform:uppercase;letter-spacing:0.5px;} ");
        sb.append(".detail-item .detail-value{font-size:14px;margin-top:2px;} ");

        // nav
        sb.append(".nav{display:flex;gap:12px;align-items:center;margin-bottom:16px;font-size:13px;} ");
        sb.append(".nav-sep{color:#cbd5e1;} ");

        // pre/code
        sb.append("pre{background:#1e293b;color:#e2e8f0;padding:16px;border-radius:6px;overflow-x:auto;font-size:12px;");
        sb.append("font-family:'SF Mono',SFMono-Regular,Consolas,'Liberation Mono',Menlo,monospace;line-height:1.6;margin-bottom:16px;} ");
        sb.append("code{font-family:'SF Mono',SFMono-Regular,Consolas,'Liberation Mono',Menlo,monospace;");
        sb.append("background:#f1f5f9;padding:2px 6px;border-radius:4px;font-size:12px;} ");

        sb.append("</style></head><body>");
        return sb.toString();
    }

    private String pageEnd() {
        return pageEnd(false);
    }

    private String pageEnd(boolean autoRefresh) {
        if (!autoRefresh) {
            return "</body></html>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<script>");
        sb.append("(function(){");
        sb.append("var formFocused=false;");
        sb.append("var els=document.querySelectorAll('input,button,select,textarea');");
        sb.append("for(var i=0;i<els.length;i++){");
        sb.append("els[i].addEventListener('focus',function(){formFocused=true;});");
        sb.append("els[i].addEventListener('blur',function(){formFocused=false;});");
        sb.append("}");
        sb.append("setInterval(function(){if(!formFocused){location.reload();}},2000);");
        sb.append("})();");
        sb.append("</script>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String statusBadge(String status) {
        String css = "badge";
        if (status != null) {
            String lower = status.toLowerCase(Locale.ENGLISH);
            if ("running".equals(lower)) css = "badge badge-running";
            else if ("completed".equals(lower) || "done".equals(lower)) css = "badge badge-completed";
            else if ("error".equals(lower) || "failed".equals(lower)) css = "badge badge-error";
            else if ("killed".equals(lower)) css = "badge badge-killed";
            else if ("queued".equals(lower) || "pending".equals(lower)) css = "badge badge-queued";
            else if ("waiting".equals(lower) || "blocked".equals(lower)) css = "badge badge-blocked";
            else if ("ready".equals(lower)) css = "badge badge-ready";
            else if ("sleeping".equals(lower)) css = "badge badge-sleeping";
            else if ("lost".equals(lower)) css = "badge badge-lost";
        }
        return "<span class='" + css + "'>" + escape(status) + "</span>";
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

    private String formatDuration(Long startMs, Long endMs) {
        if (startMs == null) return "";
        long end = endMs != null ? endMs.longValue() : System.currentTimeMillis();
        long ms = end - startMs.longValue();
        if (ms < 0) return "";
        if (ms < 1000) return ms + "ms";
        long secs = ms / 1000;
        if (secs < 60) return secs + "s";
        long mins = secs / 60;
        secs = secs % 60;
        if (mins < 60) return mins + "m " + secs + "s";
        long hours = mins / 60;
        mins = mins % 60;
        return hours + "h " + mins + "m";
    }

    private String formatElapsed(long ms) {
        if (ms <= 0) return "";
        if (ms < 1000) return ms + "ms";
        long secs = ms / 1000;
        if (secs < 60) return secs + "." + ((ms % 1000) / 100) + "s";
        long mins = secs / 60;
        secs = secs % 60;
        return mins + "m " + secs + "s";
    }

    private String shortId(String id) {
        if (id == null) return "";
        if (id.length() <= 12) return id;
        return id.substring(0, 8) + "...";
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
