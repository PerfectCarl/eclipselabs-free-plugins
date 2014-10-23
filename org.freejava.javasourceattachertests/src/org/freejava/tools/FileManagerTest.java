package org.freejava.tools;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.freejava.tools.handlers.FinderManager;
import org.freejava.tools.handlers.SourceFileResult;
import org.junit.Test;

public class FileManagerTest extends TestCase {

    @Test
    public void testFind() throws InterruptedException {
        FinderManager fm = new FinderManager();
        List<String> libs = new ArrayList<String>();
        libs.add("\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\commons-beanutils-1.8.3.jar");
        libs.add("\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\commons-logging-1.1.1.jar");
        List<SourceFileResult> results = new ArrayList<SourceFileResult>();
        fm.findSources(libs, results);
        while (fm.isRunning()) {
            Thread.sleep(1000);
        }

        Assert.assertTrue(results.size() > 1);

    }

}
