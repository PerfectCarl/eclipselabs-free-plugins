package org.freejava.tools;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.freejava.tools.handlers.SourceCodeFinderFacade;
import org.junit.Test;

public class SourceCodeFinderFacadeTest {

	@Test
	public void testFind() {
		SourceCodeFinderFacade finder = new SourceCodeFinderFacade();
		List results = new ArrayList();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\commons-beanutils-1.8.3.jar";
		String serviceUrl = "http://repository.apache.org/index.html";
		finder.find(binFile, serviceUrl, results);
		Assert.assertTrue(results.size() > 0);

	}

}
