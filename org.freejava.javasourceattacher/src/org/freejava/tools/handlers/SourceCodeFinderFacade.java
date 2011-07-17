package org.freejava.tools.handlers;

import java.util.List;

public class SourceCodeFinderFacade implements SourceCodeFinder {

	private SourceCodeFinder delegate;

	@Override
	public void find(String binFile, String serviceUrl, List<?> results) {
		SourceCodeFinder[] finders = new SourceCodeFinder[]{
				new NexusSourceCodeFinder()
		};
		for (SourceCodeFinder finder : finders) {
			if (workWithService(finder, serviceUrl)) {
				delegate = finder;
				break;
			}
		}
		delegate.find(binFile, serviceUrl, results);
	}

	private boolean workWithService(SourceCodeFinder finder, String serviceUrl) {
		InternetAccess access = new InternetAccess();
		String txt = access.getContent(serviceUrl);

		return false;
	}

	@Override
	public void cancel() {
		if (delegate != null) delegate.cancel();
	}
}
