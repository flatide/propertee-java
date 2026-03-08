package com.propertee.tests;

import com.propertee.task.Task;
import com.propertee.task.TaskEngine;
import com.propertee.task.TaskRequest;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

public class TaskEngineTest {

    @Test
    public void taskIdsShouldBeUniqueAcrossEngineInstances() throws Exception {
        File baseDir = Files.createTempDirectory("propertee-task-engine-ids").toFile();

        TaskEngine engineA = new TaskEngine(baseDir.getAbsolutePath(), "host-a");
        TaskEngine engineB = new TaskEngine(baseDir.getAbsolutePath(), "host-b");

        TaskRequest reqA = new TaskRequest();
        reqA.command = "sleep 1; echo a";
        TaskRequest reqB = new TaskRequest();
        reqB.command = "sleep 1; echo b";

        Task taskA = engineA.execute(reqA);
        Task taskB = engineB.execute(reqB);

        Assert.assertNotEquals(taskA.taskId, taskB.taskId);
        Assert.assertNotEquals(taskA.taskDir.getAbsolutePath(), taskB.taskDir.getAbsolutePath());

        engineA.waitForCompletion(taskA.taskId, 5000);
        engineB.waitForCompletion(taskB.taskId, 5000);
    }

    @Test
    public void initShouldNotDetachTasksFromSameHostInstance() throws Exception {
        File baseDir = Files.createTempDirectory("propertee-task-engine-detach").toFile();

        TaskEngine engineA = new TaskEngine(baseDir.getAbsolutePath(), "host-a");
        TaskRequest request = new TaskRequest();
        request.command = "sleep 2; echo done";

        Task task = engineA.execute(request);
        Assert.assertEquals("running", engineA.getStatusMap(task.taskId).get("status"));

        TaskEngine engineB = new TaskEngine(baseDir.getAbsolutePath(), "host-a");
        engineB.init();

        Assert.assertEquals("running", engineB.getStatusMap(task.taskId).get("status"));
        Assert.assertEquals("running", engineA.getStatusMap(task.taskId).get("status"));
        engineA.waitForCompletion(task.taskId, 5000);
    }

    @Test
    public void cancelShouldKillDescendantProcesses() throws Exception {
        File baseDir = Files.createTempDirectory("propertee-task-engine-cancel").toFile();
        File childPidFile = new File(baseDir, "child.pid");

        TaskEngine engine = new TaskEngine(baseDir.getAbsolutePath(), "host-a");
        TaskRequest request = new TaskRequest();
        request.command = "sleep 30 & echo $! > '" + shellEscape(childPidFile.getAbsolutePath()) + "'; wait";

        Task task = engine.execute(request);
        waitForFile(childPidFile, 3000);
        String childPid = readFile(childPidFile).trim();

        Assert.assertTrue(engine.killTask(task.taskId));

        Process probe = new ProcessBuilder("kill", "-0", childPid).start();
        int alive = probe.waitFor();
        Assert.assertNotEquals(0, alive);
    }

    @Test
    public void cancelShouldStopShellBeforeFollowupCommandsRun() throws Exception {
        File baseDir = Files.createTempDirectory("propertee-task-engine-kill-shell").toFile();

        TaskEngine engine = new TaskEngine(baseDir.getAbsolutePath(), "host-a");
        TaskRequest request = new TaskRequest();
        request.command = "sleep 5; echo never";

        Task task = engine.execute(request);
        Assert.assertTrue(engine.killTask(task.taskId));

        Task finished = engine.waitForCompletion(task.taskId, 3000);
        Assert.assertNotNull(finished);
        Assert.assertFalse(finished.alive);
        Assert.assertFalse(engine.getStdout(task.taskId).contains("never"));
    }

    @Test
    public void waitShouldSeeKilledStatusFromExternalKill() throws Exception {
        File baseDir = Files.createTempDirectory("propertee-task-engine-ext-kill").toFile();

        TaskEngine engineA = new TaskEngine(baseDir.getAbsolutePath(), "host-a");
        TaskRequest request = new TaskRequest();
        request.command = "sleep 30";

        Task task = engineA.execute(request);
        Assert.assertEquals("running", engineA.getStatusMap(task.taskId).get("status"));

        // Kill from a separate engine instance (simulates external kill)
        TaskEngine engineB = new TaskEngine(baseDir.getAbsolutePath(), "host-a");
        Assert.assertTrue(engineB.killTask(task.taskId));

        // waitForCompletion should see "killed", not "lost"
        Task result = engineA.waitForCompletion(task.taskId, 5000);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.alive);
        Assert.assertEquals("killed", result.status);
    }

    @Test
    public void archiveShouldPreserveTaskSummaryAndOutputTails() throws Exception {
        String oldRetention = System.getProperty("propertee.task.retentionMs");
        String oldArchiveRetention = System.getProperty("propertee.task.archiveRetentionMs");
        System.setProperty("propertee.task.retentionMs", "0");
        System.setProperty("propertee.task.archiveRetentionMs", "86400000");
        try {
            File baseDir = Files.createTempDirectory("propertee-task-engine-archive").toFile();
            TaskEngine engine = new TaskEngine(baseDir.getAbsolutePath(), "host-a");

            TaskRequest request = new TaskRequest();
            request.command = "printf 'out1\\nout2\\n'; printf 'err1\\n' 1>&2";

            Task task = engine.execute(request);
            Task finished = engine.waitForCompletion(task.taskId, 5000);
            Assert.assertNotNull(finished);
            Assert.assertEquals("completed", finished.status);

            engine.archiveExpiredTasks();

            Task archived = engine.getTask(task.taskId);
            Assert.assertNotNull(archived);
            Assert.assertTrue(archived.archived);
            Assert.assertEquals("completed", archived.status);
            Assert.assertTrue(engine.getStdout(task.taskId).contains("out1"));
            Assert.assertTrue(engine.getStdout(task.taskId).contains("out2"));
            Assert.assertTrue(engine.getStderr(task.taskId).contains("err1"));
            Assert.assertTrue(engine.listTasks().get(0).archived);
        } finally {
            restoreProperty("propertee.task.retentionMs", oldRetention);
            restoreProperty("propertee.task.archiveRetentionMs", oldArchiveRetention);
        }
    }

    @Test
    public void archivedTaskShouldBePurgedAfterArchiveRetention() throws Exception {
        String oldRetention = System.getProperty("propertee.task.retentionMs");
        String oldArchiveRetention = System.getProperty("propertee.task.archiveRetentionMs");
        System.setProperty("propertee.task.retentionMs", "0");
        System.setProperty("propertee.task.archiveRetentionMs", "0");
        try {
            File baseDir = Files.createTempDirectory("propertee-task-engine-purge").toFile();
            TaskEngine engine = new TaskEngine(baseDir.getAbsolutePath(), "host-a");

            TaskRequest request = new TaskRequest();
            request.command = "echo purge-me";

            Task task = engine.execute(request);
            Task finished = engine.waitForCompletion(task.taskId, 5000);
            Assert.assertNotNull(finished);

            engine.archiveExpiredTasks();
            Assert.assertNotNull(engine.getTask(task.taskId));

            engine.archiveExpiredTasks();
            Assert.assertNull(engine.getTask(task.taskId));
        } finally {
            restoreProperty("propertee.task.retentionMs", oldRetention);
            restoreProperty("propertee.task.archiveRetentionMs", oldArchiveRetention);
        }
    }

    private static void waitForFile(File file, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!file.exists() && (System.currentTimeMillis() - start) < timeoutMs) {
            Thread.sleep(50);
        }
        if (!file.exists()) {
            Assert.fail("Timed out waiting for file: " + file.getAbsolutePath());
        }
    }

    private static String readFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = fis.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toString("UTF-8");
        } finally {
            fis.close();
        }
    }

    private static String shellEscape(String value) {
        return value.replace("'", "'\"'\"'");
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
