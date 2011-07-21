package org.freejava.tools.handlers;

import java.util.List;

public class SourceCodeFinderFacade implements SourceCodeFinder {

	private SourceCodeFinder[] finders = new SourceCodeFinder[]{
			new NexusSourceCodeFinder(),
			new MavenRepoSourceCodeFinder(),
			new GoogleSourceCodeFinder()
	};

	private SourceCodeFinder delegate;

	@Override
	public boolean support(String serviceUrl) {
		boolean result = false;
		for (SourceCodeFinder finder : finders) {
			if (finder.support(serviceUrl)) {
				result = true;
				break;
			}
		}
		return result;
	}

	@Override
	public void find(String binFile, String serviceUrl, List<SourceFileResult> results) {
		delegate = null;
		for (SourceCodeFinder finder : finders) {
			if (finder.support(serviceUrl)) {
				delegate = finder;
				break;
			}
		}
		if (delegate != null)
			delegate.find(binFile, serviceUrl, results);
		else System.out.println("No provider for service: " + serviceUrl);
	}

	@Override
	public void cancel() {
		if (delegate != null) delegate.cancel();
	}

}
