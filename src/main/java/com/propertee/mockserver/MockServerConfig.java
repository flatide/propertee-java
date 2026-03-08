package com.propertee.mockserver;

import java.io.File;
import java.io.IOException;

public class MockServerConfig {
    public String bindAddress = "127.0.0.1";
    public int port = 18080;
    public File scriptsRoot;
    public File dataDir;
    public int maxConcurrentRuns = 4;
    public String apiToken;

    public static MockServerConfig fromSystemProperties() {
        MockServerConfig config = new MockServerConfig();
        String bind = System.getProperty("propertee.mock.bind");
        if (bind != null && bind.trim().length() > 0) {
            config.bindAddress = bind.trim();
        }
        String port = System.getProperty("propertee.mock.port");
        if (port != null && port.trim().length() > 0) {
            config.port = Integer.parseInt(port.trim());
        }
        String scriptsRoot = System.getProperty("propertee.mock.scriptsRoot");
        if (scriptsRoot == null || scriptsRoot.trim().length() == 0) {
            throw new IllegalArgumentException("System property propertee.mock.scriptsRoot is required");
        }
        String dataDir = System.getProperty("propertee.mock.dataDir");
        if (dataDir == null || dataDir.trim().length() == 0) {
            throw new IllegalArgumentException("System property propertee.mock.dataDir is required");
        }
        String maxRuns = System.getProperty("propertee.mock.maxRuns");
        if (maxRuns != null && maxRuns.trim().length() > 0) {
            config.maxConcurrentRuns = Integer.parseInt(maxRuns.trim());
        }
        String apiToken = System.getProperty("propertee.mock.apiToken");
        if (apiToken != null && apiToken.trim().length() > 0) {
            config.apiToken = apiToken.trim();
        }
        config.scriptsRoot = canonicalFile(new File(scriptsRoot.trim()));
        config.dataDir = canonicalFile(new File(dataDir.trim()));
        return config;
    }

    private static File canonicalFile(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to resolve path: " + file.getPath(), e);
        }
    }
}
