package org.freejava.tools.handlers;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;


public class MavenRepoSourceCodeFinderTest extends TestCase {

	/**
	 * Construct new test instance
	 *
	 * @param name the test name
	 */
	public MavenRepoSourceCodeFinderTest(String name) {
		super(name);
	}

	/**
	 * Run the void find(String, String, List) method test
	 */
	public void testFind() {

		MavenRepoSourceCodeFinder finder = new MavenRepoSourceCodeFinder();
		List results = new ArrayList();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\commons-beanutils-1.8.3.jar";
		String serviceUrl = "http://repository.sonatype.org/index.html";
		finder.find(binFile, serviceUrl, results);
		Assert.assertTrue(results.size() > 0);
	}
}
