package org.freejava.tools;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.freejava.tools.handlers.ArtifactorySourceCodeFinder;
import org.freejava.tools.handlers.SourceFileResult;


public class ArtifactorySourceCodeFinderTest extends TestCase {

	public void testFind() {

		ArtifactorySourceCodeFinder finder = new ArtifactorySourceCodeFinder("https://repository.cloudera.com/artifactory/webapp/home.html");
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\commons-beanutils-1.8.3.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}
}
