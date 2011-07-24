package org.freejava.tools;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.freejava.tools.handlers.GoogleSourceCodeFinder;
import org.freejava.tools.handlers.SourceFileResult;


public class GoogleSourceCodeFinderTest extends TestCase {

	public void testFind() {
		GoogleSourceCodeFinder finder = new GoogleSourceCodeFinder();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\bcprov-jdk15-145.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}
	public void testFind2() {
		GoogleSourceCodeFinder finder = new GoogleSourceCodeFinder();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\jdom.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}
	public void testFind3() {
		GoogleSourceCodeFinder finder = new GoogleSourceCodeFinder();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\nekohtml.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}
	public void testFind4() {
		GoogleSourceCodeFinder finder = new GoogleSourceCodeFinder();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\json-lib-2.4-jdk15.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}
	public void testFind5() {
		GoogleSourceCodeFinder finder = new GoogleSourceCodeFinder();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\nexus-indexer-lucene-plugin-client.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}
	public void testFind6() {
		GoogleSourceCodeFinder finder = new GoogleSourceCodeFinder();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\nexus-rest-api-client.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}
	public void testFind7() {
		GoogleSourceCodeFinder finder = new GoogleSourceCodeFinder();
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		String binFile = "\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\silvertunnel.org_netlib.jar";
		finder.find(binFile, results);
		Assert.assertTrue(results.size() > 0);
	}

}
