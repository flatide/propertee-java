package com.propertee.tests;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.propertee.mockserver.MockAdminServer;
import com.propertee.mockserver.MockServerConfig;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MockServerTest {
    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    private final Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();

    @Test
    public void serverShouldExposeRunThreadsAndTasks() throws Exception {
        TestServer testServer = createServer();
        try {
            writeScript(testServer.scriptsRoot, "multi_tasks.pt",
                "function worker(name) do\n" +
                "    taskId = START_TASK(\"sleep 2; echo \" + name)\n" +
                "    return WAIT_TASK(taskId, 5000)\n" +
                "end\n\n" +
                "multi result do\n" +
                "    thread alpha: worker(\"alpha\")\n" +
                "    thread beta: worker(\"beta\")\n" +
                "end\n\n" +
                "PRINT(result.alpha.ok)\n" +
                "PRINT(result.beta.ok)\n");

            Map<String, Object> submit = new LinkedHashMap<String, Object>();
            submit.put("scriptPath", "multi_tasks.pt");
            submit.put("props", new LinkedHashMap<String, Object>());
            Map<String, Object> created = postJson(testServer.baseUrl + "/api/runs", submit, 202);
            String runId = (String) created.get("runId");
            Assert.assertNotNull(runId);

            Map<String, Object> detail = waitForRunWithTasks(testServer.baseUrl, runId, 2, 3, 8000L);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> threads = (List<Map<String, Object>>) detail.get("threads");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) detail.get("tasks");

            Assert.assertTrue(hasThreadName(threads, "main"));
            Assert.assertTrue(hasThreadResultKey(threads, "alpha"));
            Assert.assertTrue(hasThreadResultKey(threads, "beta"));
            Assert.assertEquals(2, tasks.size());

            Map<String, Object> completed = waitForRunStatus(testServer.baseUrl, runId, "COMPLETED", 10000L);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> completedTasks = (List<Map<String, Object>>) completed.get("tasks");
            Assert.assertEquals(2, completedTasks.size());
        } finally {
            testServer.close();
        }
    }

    @Test
    public void serverShouldAllowKillingTaskFromAdminApi() throws Exception {
        TestServer testServer = createServer();
        try {
            writeScript(testServer.scriptsRoot, "kill_task.pt",
                "taskId = START_TASK(\"sleep 30\")\n" +
                "result = WAIT_TASK(taskId, 60000)\n" +
                "PRINT(result.status)\n");

            Map<String, Object> submit = new LinkedHashMap<String, Object>();
            submit.put("scriptPath", "kill_task.pt");
            submit.put("props", new LinkedHashMap<String, Object>());
            Map<String, Object> created = postJson(testServer.baseUrl + "/api/runs", submit, 202);
            String runId = (String) created.get("runId");
            Assert.assertNotNull(runId);

            Map<String, Object> detail = waitForRunWithTasks(testServer.baseUrl, runId, 1, 1, 8000L);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) detail.get("tasks");
            String taskId = (String) tasks.get(0).get("taskId");

            Map<String, Object> killResult = postJson(testServer.baseUrl + "/api/tasks/" + taskId + "/kill", new LinkedHashMap<String, Object>(), 200);
            Assert.assertEquals(Boolean.TRUE, killResult.get("killed"));

            Map<String, Object> taskDetail = waitForTaskStatus(testServer.baseUrl, taskId, "killed", 8000L);
            @SuppressWarnings("unchecked")
            Map<String, Object> taskInfo = (Map<String, Object>) taskDetail.get("task");
            Assert.assertEquals("killed", taskInfo.get("status"));
        } finally {
            testServer.close();
        }
    }

    @Test
    public void serverShouldExposeStructuredResultContract() throws Exception {
        TestServer testServer = createServer();
        try {
            writeScript(testServer.scriptsRoot, "return_result.pt",
                "return {\"ok\": true, \"value\": 42}\n");
            writeScript(testServer.scriptsRoot, "variable_result.pt",
                "value = 41\n" +
                "result = {\"ok\": true, \"value\": value + 1}\n");

            Map<String, Object> submitReturn = new LinkedHashMap<String, Object>();
            submitReturn.put("scriptPath", "return_result.pt");
            submitReturn.put("props", new LinkedHashMap<String, Object>());
            String returnRunId = (String) postJson(testServer.baseUrl + "/api/runs", submitReturn, 202).get("runId");

            Map<String, Object> submitVariable = new LinkedHashMap<String, Object>();
            submitVariable.put("scriptPath", "variable_result.pt");
            submitVariable.put("props", new LinkedHashMap<String, Object>());
            String variableRunId = (String) postJson(testServer.baseUrl + "/api/runs", submitVariable, 202).get("runId");

            Map<String, Object> returnDetail = waitForRunStatus(testServer.baseUrl, returnRunId, "COMPLETED", 8000L);
            @SuppressWarnings("unchecked")
            Map<String, Object> returnRun = (Map<String, Object>) returnDetail.get("run");
            Assert.assertEquals(Boolean.TRUE, returnRun.get("hasExplicitReturn"));
            @SuppressWarnings("unchecked")
            Map<String, Object> returnData = (Map<String, Object>) returnRun.get("resultData");
            Assert.assertEquals(Boolean.TRUE, returnData.get("ok"));
            Assert.assertEquals(42.0, ((Number) returnData.get("value")).doubleValue(), 0.0);

            Map<String, Object> variableDetail = waitForRunStatus(testServer.baseUrl, variableRunId, "COMPLETED", 8000L);
            @SuppressWarnings("unchecked")
            Map<String, Object> variableRun = (Map<String, Object>) variableDetail.get("run");
            Assert.assertEquals(Boolean.FALSE, variableRun.get("hasExplicitReturn"));
            @SuppressWarnings("unchecked")
            Map<String, Object> variableData = (Map<String, Object>) variableRun.get("resultData");
            Assert.assertEquals(Boolean.TRUE, variableData.get("ok"));
            Assert.assertEquals(42.0, ((Number) variableData.get("value")).doubleValue(), 0.0);
        } finally {
            testServer.close();
        }
    }

    @Test
    public void serverShouldRequireBearerTokenWhenConfigured() throws Exception {
        TestServer testServer = createServer("secret-token");
        try {
            writeScript(testServer.scriptsRoot, "auth_result.pt",
                "result = {\"ok\": true}\n");

            assertStatus(testServer.baseUrl + "/api/runs", "GET", null, null, 401);

            Map<String, Object> submit = new LinkedHashMap<String, Object>();
            submit.put("scriptPath", "auth_result.pt");
            submit.put("props", new LinkedHashMap<String, Object>());

            assertStatus(testServer.baseUrl + "/api/runs", "POST", submit, null, 401);

            Map<String, Object> created = postJson(testServer.baseUrl + "/api/runs", submit, 202, "secret-token");
            String runId = (String) created.get("runId");
            Assert.assertNotNull(runId);

            Map<String, Object> detail = waitForRunStatus(testServer.baseUrl, runId, "COMPLETED", 8000L, "secret-token");
            @SuppressWarnings("unchecked")
            Map<String, Object> run = (Map<String, Object>) detail.get("run");
            Assert.assertEquals(Boolean.FALSE, run.get("hasExplicitReturn"));
        } finally {
            testServer.close();
        }
    }

    @Test
    public void serverShouldSupportRunAndTaskQueryParameters() throws Exception {
        TestServer testServer = createServer();
        try {
            writeScript(testServer.scriptsRoot, "query_a.pt",
                "taskA = START_TASK(\"echo a1\")\n" +
                "taskB = START_TASK(\"echo a2\")\n" +
                "result = [WAIT_TASK(taskA, 5000), WAIT_TASK(taskB, 5000)]\n");
            writeScript(testServer.scriptsRoot, "query_b.pt",
                "result = {\"name\": \"b\"}\n");

            Map<String, Object> submitA = new LinkedHashMap<String, Object>();
            submitA.put("scriptPath", "query_a.pt");
            submitA.put("props", new LinkedHashMap<String, Object>());
            String runA = (String) postJson(testServer.baseUrl + "/api/runs", submitA, 202).get("runId");

            Map<String, Object> submitB = new LinkedHashMap<String, Object>();
            submitB.put("scriptPath", "query_b.pt");
            submitB.put("props", new LinkedHashMap<String, Object>());
            String runB = (String) postJson(testServer.baseUrl + "/api/runs", submitB, 202).get("runId");

            waitForRunStatus(testServer.baseUrl, runA, "COMPLETED", 8000L);
            waitForRunStatus(testServer.baseUrl, runB, "COMPLETED", 8000L);

            List<Map<String, Object>> completedRuns = getJsonList(testServer.baseUrl + "/api/runs?status=COMPLETED&offset=0&limit=1", 200);
            Assert.assertEquals(1, completedRuns.size());
            @SuppressWarnings("unchecked")
            String runStatus = String.valueOf(completedRuns.get(0).get("status"));
            Assert.assertEquals("COMPLETED", runStatus);

            Map<String, Object> filteredTasks = getJsonMap(testServer.baseUrl + "/api/tasks?runId=" + runA + "&status=completed&offset=0&limit=1", 200);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) filteredTasks.get("tasks");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> detached = (List<Map<String, Object>>) filteredTasks.get("detached");
            Assert.assertEquals(1, tasks.size());
            Assert.assertTrue(detached.isEmpty());
            Assert.assertEquals(runA, tasks.get(0).get("runId"));
            Assert.assertEquals("completed", tasks.get(0).get("status"));
        } finally {
            testServer.close();
        }
    }

    @Test
    public void serverShouldExposeTimeoutExceededOnTask() throws Exception {
        TestServer testServer = createServer();
        try {
            writeScript(testServer.scriptsRoot, "timeout_task.pt",
                "taskId = START_TASK(\"sleep 1\", {\"timeout\": 10})\n" +
                "PRINT(taskId)\n");

            Map<String, Object> submit = new LinkedHashMap<String, Object>();
            submit.put("scriptPath", "timeout_task.pt");
            submit.put("props", new LinkedHashMap<String, Object>());
            String runId = (String) postJson(testServer.baseUrl + "/api/runs", submit, 202).get("runId");

            Map<String, Object> detail = waitForRunWithTasks(testServer.baseUrl, runId, 1, 1, 8000L);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) detail.get("tasks");
            String taskId = (String) tasks.get(0).get("taskId");

            Map<String, Object> taskDetail = waitForTaskTimeoutExceeded(testServer.baseUrl, taskId, 4000L);
            @SuppressWarnings("unchecked")
            Map<String, Object> taskInfo = (Map<String, Object>) taskDetail.get("task");
            @SuppressWarnings("unchecked")
            Map<String, Object> observation = (Map<String, Object>) taskDetail.get("observation");
            Assert.assertEquals(Boolean.TRUE, taskInfo.get("timeoutExceeded"));
            Assert.assertEquals(Boolean.TRUE, observation.get("timeoutExceeded"));
        } finally {
            testServer.close();
        }
    }

    @Test
    public void serverShouldArchiveOldRuns() throws Exception {
        String oldRunRetention = System.getProperty("propertee.mock.runRetentionMs");
        String oldRunArchiveRetention = System.getProperty("propertee.mock.runArchiveRetentionMs");
        System.setProperty("propertee.mock.runRetentionMs", "0");
        System.setProperty("propertee.mock.runArchiveRetentionMs", "86400000");
        try {
            TestServer testServer = createServer();
            try {
                writeScript(testServer.scriptsRoot, "archive_run.pt",
                    "PRINT(\"line1\")\n" +
                    "PRINT(\"line2\")\n" +
                    "result = {\"ok\": true}\n");

                Map<String, Object> submit = new LinkedHashMap<String, Object>();
                submit.put("scriptPath", "archive_run.pt");
                submit.put("props", new LinkedHashMap<String, Object>());
                String runId = (String) postJson(testServer.baseUrl + "/api/runs", submit, 202).get("runId");

                Map<String, Object> completedDetail = waitForRunStatus(testServer.baseUrl, runId, "COMPLETED", 8000L);
                @SuppressWarnings("unchecked")
                Map<String, Object> completedRun = (Map<String, Object>) completedDetail.get("run");
                Assert.assertEquals(Boolean.TRUE, completedRun.get("archived"));

                List<Map<String, Object>> runsAfterArchive = getJsonList(testServer.baseUrl + "/api/runs", 200);
                Assert.assertTrue(containsRun(runsAfterArchive, runId));
            } finally {
                testServer.close();
            }
        } finally {
            restoreProperty("propertee.mock.runRetentionMs", oldRunRetention);
            restoreProperty("propertee.mock.runArchiveRetentionMs", oldRunArchiveRetention);
        }
    }

    @Test
    public void serverShouldPurgeArchivedRuns() throws Exception {
        String oldRunRetention = System.getProperty("propertee.mock.runRetentionMs");
        String oldRunArchiveRetention = System.getProperty("propertee.mock.runArchiveRetentionMs");
        System.setProperty("propertee.mock.runRetentionMs", "0");
        System.setProperty("propertee.mock.runArchiveRetentionMs", "100");
        try {
            TestServer testServer = createServer();
            try {
                writeScript(testServer.scriptsRoot, "purge_run.pt",
                    "result = {\"ok\": true}\n");

                Map<String, Object> submit = new LinkedHashMap<String, Object>();
                submit.put("scriptPath", "purge_run.pt");
                submit.put("props", new LinkedHashMap<String, Object>());
                String runId = (String) postJson(testServer.baseUrl + "/api/runs", submit, 202).get("runId");

                waitForRunAbsentFromList(testServer.baseUrl, runId, 8000L);
            } finally {
                testServer.close();
            }
        } finally {
            restoreProperty("propertee.mock.runRetentionMs", oldRunRetention);
            restoreProperty("propertee.mock.runArchiveRetentionMs", oldRunArchiveRetention);
        }
    }

    private boolean hasThreadName(List<Map<String, Object>> threads, String name) {
        for (Map<String, Object> thread : threads) {
            if (name.equals(thread.get("name"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTaskThread(List<Map<String, Object>> tasks, String threadName) {
        for (Map<String, Object> task : tasks) {
            if (threadName.equals(task.get("threadName"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasThreadResultKey(List<Map<String, Object>> threads, String resultKeyName) {
        for (Map<String, Object> thread : threads) {
            if (resultKeyName.equals(thread.get("resultKeyName"))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsRun(List<Map<String, Object>> runs, String runId) {
        for (Map<String, Object> run : runs) {
            if (runId.equals(run.get("runId"))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> waitForRunWithTasks(String baseUrl, String runId, int taskCount, int minThreads, long timeoutMs) throws Exception {
        return waitForRunWithTasks(baseUrl, runId, taskCount, minThreads, timeoutMs, null);
    }

    private Map<String, Object> waitForRunWithTasks(String baseUrl, String runId, int taskCount, int minThreads, long timeoutMs, String bearerToken) throws Exception {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < timeoutMs) {
            Map<String, Object> detail = getJsonMap(baseUrl + "/api/runs/" + runId, 200, bearerToken);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) detail.get("tasks");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> threads = (List<Map<String, Object>>) detail.get("threads");
            if (tasks.size() >= taskCount && threads.size() >= minThreads) {
                return detail;
            }
            Thread.sleep(100L);
        }
        Assert.fail("Timed out waiting for run tasks: " + runId);
        return null;
    }

    private Map<String, Object> waitForRunStatus(String baseUrl, String runId, String status, long timeoutMs) throws Exception {
        return waitForRunStatus(baseUrl, runId, status, timeoutMs, null);
    }

    private Map<String, Object> waitForRunStatus(String baseUrl, String runId, String status, long timeoutMs, String bearerToken) throws Exception {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < timeoutMs) {
            Map<String, Object> detail = getJsonMap(baseUrl + "/api/runs/" + runId, 200, bearerToken);
            @SuppressWarnings("unchecked")
            Map<String, Object> run = (Map<String, Object>) detail.get("run");
            if (status.equals(run.get("status"))) {
                return detail;
            }
            Thread.sleep(100L);
        }
        Assert.fail("Timed out waiting for run status " + status + ": " + runId);
        return null;
    }

    private Map<String, Object> waitForTaskStatus(String baseUrl, String taskId, String status, long timeoutMs) throws Exception {
        return waitForTaskStatus(baseUrl, taskId, status, timeoutMs, null);
    }

    private Map<String, Object> waitForTaskStatus(String baseUrl, String taskId, String status, long timeoutMs, String bearerToken) throws Exception {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < timeoutMs) {
            Map<String, Object> detail = getJsonMap(baseUrl + "/api/tasks/" + taskId, 200, bearerToken);
            @SuppressWarnings("unchecked")
            Map<String, Object> task = (Map<String, Object>) detail.get("task");
            if (status.equals(task.get("status"))) {
                return detail;
            }
            Thread.sleep(100L);
        }
        Assert.fail("Timed out waiting for task status " + status + ": " + taskId);
        return null;
    }

    private Map<String, Object> waitForTaskTimeoutExceeded(String baseUrl, String taskId, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < timeoutMs) {
            Map<String, Object> detail = getJsonMap(baseUrl + "/api/tasks/" + taskId, 200);
            @SuppressWarnings("unchecked")
            Map<String, Object> task = (Map<String, Object>) detail.get("task");
            if (Boolean.TRUE.equals(task.get("timeoutExceeded"))) {
                return detail;
            }
            Thread.sleep(100L);
        }
        Assert.fail("Timed out waiting for task timeoutExceeded: " + taskId);
        return null;
    }

    private void waitForRunAbsentFromList(String baseUrl, String runId, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < timeoutMs) {
            List<Map<String, Object>> runs = getJsonList(baseUrl + "/api/runs", 200);
            if (!containsRun(runs, runId)) {
                return;
            }
            Thread.sleep(100L);
        }
        Assert.fail("Timed out waiting for run purge: " + runId);
    }

    private TestServer createServer() throws Exception {
        return createServer(null);
    }

    private TestServer createServer(String apiToken) throws Exception {
        File scriptsRoot = Files.createTempDirectory("propertee-mock-scripts").toFile();
        File dataDir = Files.createTempDirectory("propertee-mock-data").toFile();

        MockServerConfig config = new MockServerConfig();
        config.bindAddress = "127.0.0.1";
        config.port = 0;
        config.scriptsRoot = scriptsRoot;
        config.dataDir = dataDir;
        config.maxConcurrentRuns = 2;
        config.apiToken = apiToken;

        MockAdminServer server = new MockAdminServer(config);
        server.start();
        return new TestServer(server, scriptsRoot, "http://127.0.0.1:" + server.getPort());
    }

    private void writeScript(File scriptsRoot, String name, String content) throws IOException {
        File target = new File(scriptsRoot, name);
        OutputStream output = new FileOutputStream(target);
        try {
            output.write(content.getBytes(Charset.forName("UTF-8")));
        } finally {
            output.close();
        }
    }

    private Map<String, Object> postJson(String url, Map<String, Object> payload, int expectedStatus) throws IOException {
        return postJson(url, payload, expectedStatus, null);
    }

    private Map<String, Object> postJson(String url, Map<String, Object> payload, int expectedStatus, String bearerToken) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (bearerToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        byte[] body = gson.toJson(payload).getBytes("UTF-8");
        OutputStream out = conn.getOutputStream();
        try {
            out.write(body);
        } finally {
            out.close();
        }
        int status = conn.getResponseCode();
        Assert.assertEquals(expectedStatus, status);
        return readJsonMap(conn);
    }

    private Map<String, Object> getJsonMap(String url, int expectedStatus) throws IOException {
        return getJsonMap(url, expectedStatus, null);
    }

    private Map<String, Object> getJsonMap(String url, int expectedStatus, String bearerToken) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        if (bearerToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        int status = conn.getResponseCode();
        Assert.assertEquals(expectedStatus, status);
        return readJsonMap(conn);
    }

    private List<Map<String, Object>> getJsonList(String url, int expectedStatus) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        int status = conn.getResponseCode();
        Assert.assertEquals(expectedStatus, status);
        return readJsonList(conn);
    }

    private void assertStatus(String url, String method, Map<String, Object> payload, String bearerToken, int expectedStatus) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        if (bearerToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        if (payload != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = gson.toJson(payload).getBytes("UTF-8");
            OutputStream out = conn.getOutputStream();
            try {
                out.write(body);
            } finally {
                out.close();
            }
        }
        int status = conn.getResponseCode();
        Assert.assertEquals(expectedStatus, status);
        InputStream input = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (input != null) {
            try {
                readAll(input);
            } finally {
                input.close();
            }
        }
        conn.disconnect();
    }

    private Map<String, Object> readJsonMap(HttpURLConnection conn) throws IOException {
        InputStream input = conn.getInputStream();
        try {
            String json = readAll(input);
            return gson.fromJson(json, mapType);
        } finally {
            input.close();
            conn.disconnect();
        }
    }

    private List<Map<String, Object>> readJsonList(HttpURLConnection conn) throws IOException {
        InputStream input = conn.getInputStream();
        try {
            String json = readAll(input);
            return gson.fromJson(json, listType);
        } finally {
            input.close();
            conn.disconnect();
        }
    }

    private String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = input.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toString("UTF-8");
    }

    private static class TestServer {
        private final MockAdminServer server;
        private final File scriptsRoot;
        private final String baseUrl;

        private TestServer(MockAdminServer server, File scriptsRoot, String baseUrl) {
            this.server = server;
            this.scriptsRoot = scriptsRoot;
            this.baseUrl = baseUrl;
        }

        private void close() {
            server.stop();
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
