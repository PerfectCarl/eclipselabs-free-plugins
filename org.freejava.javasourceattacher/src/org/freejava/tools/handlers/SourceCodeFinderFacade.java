package org.freejava.tools.handlers;

import java.util.List;

public class SourceCodeFinderFacade implements SourceCodeFinder {
/*
            // Nexus

            // Maven Central

            // jarvana
            "http://www.jarvana.com",

            // mvnsearch
            "http://www.mvnsearch.org/",

            // findjar
            "http://www.findjar.com/index.x",

            // Artifact Repository
            "http://www.artifact-repository.org",

            // mvnrepository
            "http://mvnrepository.com",

            // mvnbrowser
            "http://www.mvnbrowser.com",

            // mavenreposearch
            "http://www.mavenreposearch.com/",

            // ozacc
            "http://maven.ozacc.com/",

            // google
*/
	private SourceCodeFinder[] finders = new SourceCodeFinder[]{
			new NexusSourceCodeFinder("http://repository.sonatype.org/index.html"),
			new NexusSourceCodeFinder("https://repository.apache.org/index.html"),
			new NexusSourceCodeFinder("https://repository.jboss.org/nexus/index.html"),
			new NexusSourceCodeFinder("http://oss.sonatype.org/index.html"),
			new NexusSourceCodeFinder("http://repository.ow2.org/nexus/index.html"),
			new NexusSourceCodeFinder("https://nexus.codehaus.org/index.html"),
			new NexusSourceCodeFinder("http://maven2.exoplatform.org/index.html"),
			new NexusSourceCodeFinder("http://maven.nuxeo.org/nexus/index.html"),
			new NexusSourceCodeFinder("http://maven.alfresco.com/nexus/index.html"),
			new NexusSourceCodeFinder("https://repository.cloudera.com/index.html"),
			new NexusSourceCodeFinder("http://nexus.xwiki.org/nexus/index.html"),
			new MavenRepoSourceCodeFinder(),
			new GoogleSourceCodeFinder()
	};

	private boolean canceled;

	@Override
	public void find(String binFile, List<SourceFileResult> results) {
		for (int i = 0; i < finders.length && results.isEmpty() && !canceled; i++) {
			SourceCodeFinder finder = finders[0];
			finder.find(binFile, results);
		}
	}

	@Override
	public void cancel() {
		canceled = true;
		for (int i = 0; i < finders.length && !canceled; i++) {
			SourceCodeFinder finder = finders[0];
			finder.cancel();
		}
	}

}
