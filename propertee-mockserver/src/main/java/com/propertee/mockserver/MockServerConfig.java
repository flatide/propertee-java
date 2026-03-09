package com.propertee.mockserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MockServerConfig {
    public String bindAddress = "127.0.0.1";
    public int port = 18080;
    public File scriptsRoot;
    public File dataDir;
    public int maxConcurrentRuns = 4;
    public String apiToken;

    public static MockServerConfig fromArgs(String[] args) {
        File configFile = resolveConfigFile(args);
        Properties fileProps = loadProperties(configFile);
        return fromSources(fileProps);
    }

    public static MockServerConfig fromSystemProperties() {
        return fromSources(new Properties());
    }

    private static MockServerConfig fromSources(Properties fileProps) {
        MockServerConfig config = new MockServerConfig();
        String bind = getSetting("propertee.mock.bind", fileProps);
        if (bind != null && bind.trim().length() > 0) {
            config.bindAddress = bind.trim();
        }
        String port = getSetting("propertee.mock.port", fileProps);
        if (port != null && port.trim().length() > 0) {
            config.port = Integer.parseInt(port.trim());
        }
        String scriptsRoot = getSetting("propertee.mock.scriptsRoot", fileProps);
        if (scriptsRoot == null || scriptsRoot.trim().length() == 0) {
            throw new IllegalArgumentException("Mock server setting propertee.mock.scriptsRoot is required");
        }
        String dataDir = getSetting("propertee.mock.dataDir", fileProps);
        if (dataDir == null || dataDir.trim().length() == 0) {
            throw new IllegalArgumentException("Mock server setting propertee.mock.dataDir is required");
        }
        String maxRuns = getSetting("propertee.mock.maxRuns", fileProps);
        if (maxRuns != null && maxRuns.trim().length() > 0) {
            config.maxConcurrentRuns = Integer.parseInt(maxRuns.trim());
        }
        String apiToken = getSetting("propertee.mock.apiToken", fileProps);
        if (apiToken != null && apiToken.trim().length() > 0) {
            config.apiToken = apiToken.trim();
        }
        config.scriptsRoot = canonicalFile(new File(scriptsRoot.trim()));
        config.dataDir = canonicalFile(new File(dataDir.trim()));
        return config;
    }

    private static String getSetting(String key, Properties fileProps) {
        String sysValue = System.getProperty(key);
        if (sysValue != null && sysValue.trim().length() > 0) {
            return sysValue;
        }
        String fileValue = fileProps.getProperty(key);
        if (fileValue != null && fileValue.trim().length() > 0) {
            return fileValue;
        }
        return null;
    }

    private static File resolveConfigFile(String[] args) {
        String configured = System.getProperty("propertee.mock.config");
        if (configured != null && configured.trim().length() > 0) {
            return canonicalFile(new File(configured.trim()));
        }
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--config".equals(arg) || "-c".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(arg + " requires a file path");
                }
                return canonicalFile(new File(args[i + 1]));
            }
            if (arg != null && arg.startsWith("--config=")) {
                return canonicalFile(new File(arg.substring("--config=".length())));
            }
        }
        return null;
    }

    private static Properties loadProperties(File file) {
        Properties props = new Properties();
        if (file == null) {
            return props;
        }
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Mock server config file not found: " + file.getPath());
        }
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            props.load(input);
            return props;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read mock server config: " + file.getPath(), e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static File canonicalFile(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to resolve path: " + file.getPath(), e);
        }
    }
}
