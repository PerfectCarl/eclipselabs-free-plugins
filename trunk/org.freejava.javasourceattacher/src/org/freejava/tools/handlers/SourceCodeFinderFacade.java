package org.freejava.tools.handlers;

import java.util.List;

public class SourceCodeFinderFacade implements SourceCodeFinder {

	private SourceCodeFinder[] finders = new SourceCodeFinder[]{
			new NexusSourceCodeFinder()
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
	public void find(String binFile, String serviceUrl, List results) {

		for (SourceCodeFinder finder : finders) {
			if (finder.support(serviceUrl)) {
				delegate = finder;
				break;
			}
		}
		delegate.find(binFile, serviceUrl, results);
	}

	@Override
	public void cancel() {
		if (delegate != null) delegate.cancel();
	}

}
