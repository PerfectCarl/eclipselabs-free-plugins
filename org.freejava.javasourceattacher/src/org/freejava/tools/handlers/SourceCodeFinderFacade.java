package org.freejava.tools.handlers;

import java.util.ArrayList;
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
			new MavenRepoSourceCodeFinder(),
			new NexusSourceCodeFinder("http://repository.sonatype.org/index.html"),
			new NexusSourceCodeFinder("https://repository.apache.org/index.html"),
			new NexusSourceCodeFinder("https://repository.jboss.org/nexus/index.html"),
			new NexusSourceCodeFinder("http://oss.sonatype.org/index.html"),
			new WebBasedArtifactorySourceCodeFinder("http://repo.springsource.org/webapp/home.html"),
			new NexusSourceCodeFinder("http://repository.ow2.org/nexus/index.html"),
			new NexusSourceCodeFinder("https://nexus.codehaus.org/index.html"),
			new NexusSourceCodeFinder("https://maven.java.net/index.html"),
			new NexusSourceCodeFinder("http://maven2.exoplatform.org/index.html"),
			new NexusSourceCodeFinder("http://maven.nuxeo.org/nexus/index.html"),
			new NexusSourceCodeFinder("http://maven.alfresco.com/nexus/index.html"),
			new ArtifactorySourceCodeFinder("https://repository.cloudera.com/artifactory/webapp/home.html"),
			new NexusSourceCodeFinder("http://nexus.xwiki.org/nexus/index.html"),

			new EclipsePluginSourceByUrlPatternFinder("http://www.mmnt.ru/int/get?st={0}"),
			new EclipsePluginSourceByUrlPatternFinder("http://www.searchftps.com/indexer/search.aspx?__LASTFOCUS=&__EVENTTARGET=ctl00%24MainContent%24SearchButton&__EVENTARGUMENT=&ctl00%24MainContent%24SearchKeywordTextBox={0}&ctl00%24MainContent%24SearchTypeDropDownList=And&ctl00%24MainContent%24SearchOrderDropDownList=DateDesc&ctl00%24MainContent%24SearchFilterDropDownList=NoFilter"),
			new EclipsePluginSourceByGoogleCSESourceCodeFinder(),

			new SourceAttacherServiceSourceCodeFinder()//,
			//new GoogleSourceCodeFinder()
	};

	private boolean canceled;

	public void find(String binFile, List<SourceFileResult> results) {
		for (int i = 0; i < finders.length && !canceled; i++) {
			List<SourceFileResult> results2 = new ArrayList<SourceFileResult>();
			SourceCodeFinder finder = finders[i];
			finder.find(binFile, results2);
			if (!results2.isEmpty()) {
				results.addAll(results2);
				break;
			}
		}
	}

	public void cancel() {
		canceled = true;
		for (int i = 0; i < finders.length && !canceled; i++) {
			SourceCodeFinder finder = finders[0];
			finder.cancel();
		}
	}

}
