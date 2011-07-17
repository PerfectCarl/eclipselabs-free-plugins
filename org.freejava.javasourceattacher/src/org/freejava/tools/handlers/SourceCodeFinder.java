package org.freejava.tools.handlers;

import java.util.List;

public interface SourceCodeFinder {
    void find(String binFile, String serviceUrl, List<?> results);
    void cancel();
}
