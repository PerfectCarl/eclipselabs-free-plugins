package org.freejava.tools;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.freejava.tools.handlers.SourceCodeFinderFacade;
import org.freejava.tools.handlers.SourceFileResult;
import org.junit.Test;

public class SourceCodeFinderFacadeTest {

	@Test
	public void testFindGAV() {
		SourceCodeFinderFacade finder = new SourceCodeFinderFacade();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\commons-beanutils-1.8.3.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}

	@Test
	public void testFindNotGAV() {
		SourceCodeFinderFacade finder = new SourceCodeFinderFacade();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\xercesImpl.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}


}
