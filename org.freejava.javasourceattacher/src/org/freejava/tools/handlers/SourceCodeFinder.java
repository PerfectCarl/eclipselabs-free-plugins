package org.freejava.tools.handlers;

import java.io.File;

public interface SourceCodeFinder {
    public File find(File bin) throws Exception;
}
