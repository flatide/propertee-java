package com.propertee.platform;

import java.util.List;

/**
 * Default PlatformProvider that rejects all operations.
 * Used when no host provides OS resource access.
 */
public class UnsupportedPlatformProvider implements PlatformProvider {
    private static final String MSG = " is not available in this environment";

    @Override public String getEnv(String name) {
        throw new UnsupportedOperationException("ENV()" + MSG);
    }
    @Override public boolean fileExists(String path) {
        throw new UnsupportedOperationException("FILE_EXISTS()" + MSG);
    }
    @Override public FileInfo fileInfo(String path) {
        throw new UnsupportedOperationException("FILE_INFO()" + MSG);
    }
    @Override public List<FileEntry> listDir(String path) {
        throw new UnsupportedOperationException("LIST_DIR()" + MSG);
    }
    @Override public List<String> readLines(String path, int start, int count) {
        throw new UnsupportedOperationException("READ_LINES()" + MSG);
    }
    @Override public void writeFile(String path, String content) {
        throw new UnsupportedOperationException("WRITE_FILE()" + MSG);
    }
    @Override public void writeLines(String path, List<String> lines) {
        throw new UnsupportedOperationException("WRITE_LINES()" + MSG);
    }
    @Override public void appendFile(String path, String content) {
        throw new UnsupportedOperationException("APPEND_FILE()" + MSG);
    }
    @Override public void mkdir(String path) {
        throw new UnsupportedOperationException("MKDIR()" + MSG);
    }
    @Override public void deleteFile(String path) {
        throw new UnsupportedOperationException("DELETE_FILE()" + MSG);
    }
}
