package org.freejava.tools.handlers;

public class SourceFileResult {
	private String url;
	int accuracy;

	public SourceFileResult(String url, int accuracy) {
		super();
		this.url = url;
		this.accuracy = accuracy;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public int getAccuracy() {
		return accuracy;
	}
	public void setAccuracy(int accuracy) {
		this.accuracy = accuracy;
	}

}
