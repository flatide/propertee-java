package com.propertee.tests;

import com.propertee.task.DefaultTaskRunner;
import com.propertee.task.Task;
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

        DefaultTaskRunner runnerA = new DefaultTaskRunner(baseDir.getAbsolutePath());
        DefaultTaskRunner runnerB = new DefaultTaskRunner(baseDir.getAbsolutePath());

        TaskRequest reqA = new TaskRequest();
        reqA.command = "sleep 1; echo a";
        TaskRequest reqB = new TaskRequest();
        reqB.command = "sleep 1; echo b";

        Task taskA = runnerA.execute(reqA);
        Task taskB = runnerB.execute(reqB);

        Assert.assertNotEquals(taskA.taskId, taskB.taskId);
        Assert.assertNotEquals(taskA.taskDir.getAbsolutePath(), taskB.taskDir.getAbsolutePath());

        runnerA.waitForCompletion(taskA.taskId, 5000);
        runnerB.waitForCompletion(taskB.taskId, 5000);
    }

    @Test
    public void cancelShouldKillDescendantProcesses() throws Exception {
        File baseDir = Files.createTempDirectory("propertee-task-engine-cancel").toFile();
        File childPidFile = new File(baseDir, "child.pid");

        DefaultTaskRunner runner = new DefaultTaskRunner(baseDir.getAbsolutePath());
        TaskRequest request = new TaskRequest();
        request.command = "sleep 30 & echo $! > '" + shellEscape(childPidFile.getAbsolutePath()) + "'; wait";

        Task task = runner.execute(request);
        waitForFile(childPidFile, 3000);
        String childPid = readFile(childPidFile).trim();

        Assert.assertTrue(runner.killTask(task.taskId));

        Process probe = new ProcessBuilder("kill", "-0", childPid).start();
        int alive = probe.waitFor();
        Assert.assertNotEquals(0, alive);
    }

    @Test
    public void cancelShouldStopShellBeforeFollowupCommandsRun() throws Exception {
        File baseDir = Files.createTempDirectory("propertee-task-engine-kill-shell").toFile();

        DefaultTaskRunner runner = new DefaultTaskRunner(baseDir.getAbsolutePath());
        TaskRequest request = new TaskRequest();
        request.command = "sleep 5; echo never";

        Task task = runner.execute(request);
        Assert.assertTrue(runner.killTask(task.taskId));

        Task finished = runner.waitForCompletion(task.taskId, 3000);
        Assert.assertNotNull(finished);
        Assert.assertFalse(finished.alive);
        Assert.assertFalse(runner.getStdout(task.taskId).contains("never"));
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
}
