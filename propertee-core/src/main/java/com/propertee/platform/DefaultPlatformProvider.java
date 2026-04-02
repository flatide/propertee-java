package com.propertee.platform;

import java.io.*;
import java.util.*;

/**
 * Unrestricted PlatformProvider with direct OS access.
 * Hosts can use this directly or extend it with restrictions
 * (allowed paths, read-only mode, etc.).
 */
public class DefaultPlatformProvider implements PlatformProvider {

    @Override
    public String getEnv(String name) {
        return System.getenv(name);
    }

    @Override
    public boolean fileExists(String path) {
        return new File(path).exists();
    }

    @Override
    public FileInfo fileInfo(String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new RuntimeException("File not found: " + path);
        }
        return new FileInfo(
            file.isDirectory() ? "dir" : "file",
            file.length(),
            file.lastModified()
        );
    }

    @Override
    public List<FileEntry> listDir(String path) {
        File dir = new File(path);
        if (!dir.isDirectory()) {
            throw new RuntimeException("Not a directory: " + path);
        }
        File[] files = dir.listFiles();
        List<FileEntry> entries = new ArrayList<FileEntry>();
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return a.getName().compareTo(b.getName());
                }
            });
            for (File f : files) {
                entries.add(new FileEntry(
                    f.getName(),
                    f.isDirectory() ? "dir" : "file",
                    f.length()
                ));
            }
        }
        return entries;
    }

    @Override
    public List<String> readLines(String path, int start, int count) {
        File file = new File(path);
        if (!file.isFile()) {
            throw new RuntimeException("File not found: " + path);
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            List<String> lines = new ArrayList<String>();
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (lineNum < start) continue;
                if (lines.size() >= count) break;
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignore) {}
            }
        }
    }

    @Override
    public void writeFile(String path, String content) {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(path), "UTF-8");
            writer.write(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignore) {}
            }
        }
    }

    @Override
    public void writeLines(String path, List<String> lines) {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(path), "UTF-8");
            for (String line : lines) {
                writer.write(line);
                writer.write("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignore) {}
            }
        }
    }

    @Override
    public void appendFile(String path, String content) {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(path, true), "UTF-8");
            writer.write(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to append to file: " + e.getMessage(), e);
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignore) {}
            }
        }
    }

    @Override
    public void mkdir(String path) {
        File dir = new File(path);
        if (dir.exists()) {
            if (dir.isDirectory()) return;
            throw new RuntimeException("Path exists but is not a directory: " + path);
        }
        if (!dir.mkdirs()) {
            throw new RuntimeException("Failed to create directory: " + path);
        }
    }

    @Override
    public void deleteFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new RuntimeException("File not found: " + path);
        }
        if (file.isDirectory()) {
            throw new RuntimeException("Cannot delete directory: " + path);
        }
        if (!file.delete()) {
            throw new RuntimeException("Failed to delete file: " + path);
        }
    }
}
