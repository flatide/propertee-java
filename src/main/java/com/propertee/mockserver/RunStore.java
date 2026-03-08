package com.propertee.mockserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class RunStore {
    private final File runsDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public RunStore(File dataDir) {
        this.runsDir = new File(dataDir, "runs");
        if (!runsDir.exists()) {
            runsDir.mkdirs();
        }
    }

    public synchronized void save(RunInfo run) {
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(fileFor(run.runId)), "UTF-8");
            gson.toJson(run, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save run " + run.runId + ": " + e.getMessage(), e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    public synchronized RunInfo load(String runId) {
        File file = fileFor(runId);
        if (!file.exists()) return null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            String json = readAll(fis);
            return gson.fromJson(json, RunInfo.class);
        } catch (IOException e) {
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    public synchronized List<RunInfo> loadAll() {
        List<RunInfo> result = new ArrayList<RunInfo>();
        File[] files = runsDir.listFiles();
        if (files == null) {
            return result;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".json")) {
                continue;
            }
            RunInfo run = load(stripSuffix(file.getName(), ".json"));
            if (run != null) {
                result.add(run);
            }
        }
        return result;
    }

    private File fileFor(String runId) {
        return new File(runsDir, runId + ".json");
    }

    private String stripSuffix(String value, String suffix) {
        if (value.endsWith(suffix)) {
            return value.substring(0, value.length() - suffix.length());
        }
        return value;
    }

    private String readAll(InputStream input) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = input.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, len, "UTF-8"));
        }
        return sb.toString();
    }
}
