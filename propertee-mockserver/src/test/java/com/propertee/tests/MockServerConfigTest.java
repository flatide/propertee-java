package com.propertee.tests;

import com.propertee.mockserver.MockServerConfig;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;

public class MockServerConfigTest {

    @Test
    public void shouldLoadSettingsFromConfigFile() throws Exception {
        File root = Files.createTempDirectory("propertee-mock-config").toFile();
        File configFile = new File(root, "mockserver.properties");
        write(configFile,
            "propertee.mock.bind=0.0.0.0\n" +
            "propertee.mock.port=19090\n" +
            "propertee.mock.scriptsRoot=" + new File(root, "scripts").getAbsolutePath() + "\n" +
            "propertee.mock.dataDir=" + new File(root, "data").getAbsolutePath() + "\n" +
            "propertee.mock.maxRuns=7\n" +
            "propertee.mock.apiToken=test-token\n");

        String oldConfig = System.getProperty("propertee.mock.config");
        String oldPort = System.getProperty("propertee.mock.port");
        System.setProperty("propertee.mock.config", configFile.getAbsolutePath());
        System.clearProperty("propertee.mock.port");
        try {
            MockServerConfig config = MockServerConfig.fromArgs(new String[0]);
            Assert.assertEquals("0.0.0.0", config.bindAddress);
            Assert.assertEquals(19090, config.port);
            Assert.assertEquals(7, config.maxConcurrentRuns);
            Assert.assertEquals("test-token", config.apiToken);
            Assert.assertEquals(new File(root, "scripts").getCanonicalPath(), config.scriptsRoot.getCanonicalPath());
            Assert.assertEquals(new File(root, "data").getCanonicalPath(), config.dataDir.getCanonicalPath());
        } finally {
            restoreProperty("propertee.mock.config", oldConfig);
            restoreProperty("propertee.mock.port", oldPort);
        }
    }

    @Test
    public void systemPropertiesShouldOverrideConfigFile() throws Exception {
        File root = Files.createTempDirectory("propertee-mock-config-override").toFile();
        File configFile = new File(root, "mockserver.properties");
        write(configFile,
            "propertee.mock.scriptsRoot=" + new File(root, "scripts").getAbsolutePath() + "\n" +
            "propertee.mock.dataDir=" + new File(root, "data").getAbsolutePath() + "\n" +
            "propertee.mock.port=18080\n");

        String oldConfig = System.getProperty("propertee.mock.config");
        String oldPort = System.getProperty("propertee.mock.port");
        System.setProperty("propertee.mock.config", configFile.getAbsolutePath());
        System.setProperty("propertee.mock.port", "19999");
        try {
            MockServerConfig config = MockServerConfig.fromArgs(new String[0]);
            Assert.assertEquals(19999, config.port);
        } finally {
            restoreProperty("propertee.mock.config", oldConfig);
            restoreProperty("propertee.mock.port", oldPort);
        }
    }

    private static void write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(content.getBytes(Charset.forName("UTF-8")));
        } finally {
            out.close();
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
