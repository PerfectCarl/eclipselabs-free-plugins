package org.freejava.tools;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.freejava.tools.handlers.NexusSourceCodeFinder;
import org.freejava.tools.handlers.SourceFileResult;
import org.junit.Test;

public class NexusSourceCodeFinderTest {

	@Test
	public void testFind() {
		NexusSourceCodeFinder finder = new NexusSourceCodeFinder("http://repository.sonatype.org/index.html");
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\commons-beanutils-1.8.3.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}

}

