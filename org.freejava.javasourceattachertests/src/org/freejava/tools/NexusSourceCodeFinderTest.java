package org.freejava.tools;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.freejava.tools.handlers.NexusSourceCodeFinder;
import org.junit.Test;

public class NexusSourceCodeFinderTest {

	@Test
	public void testFind() {
		NexusSourceCodeFinder finder = new NexusSourceCodeFinder();
		List results = new ArrayList();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\commons-beanutils-1.8.3.jar";
		String serviceUrl = "http://repository.sonatype.org/index.html";
		finder.find(binFile, serviceUrl, results);
		Assert.assertTrue(results.size() > 0);
	}

	@Test
	public void testSupport() throws Exception {
		NexusSourceCodeFinder finder = new NexusSourceCodeFinder();
		Assert.assertFalse(finder.support("http://www.mvnsearch.org/"));
	}

}

