package org.freejava.tools.handlers;

import java.util.List;

public interface SourceCodeFinder {
	boolean support(String serviceUrl);
    void find(String binFile, String serviceUrl, List results);
    void cancel();
}
