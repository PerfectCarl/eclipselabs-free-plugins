package org.freejava.tools.handlers;

public class SourceFileResult {
	private String binFile;
	private String source;
	private int accuracy;

	public SourceFileResult(String binFile, String source, int accuracy) {
		this.binFile = binFile;
		this.source = source;
		this.accuracy = accuracy;
	}

	public String getBinFile() {
		return binFile;
	}
	public void setBinFile(String binFile) {
		this.binFile = binFile;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}
	public int getAccuracy() {
		return accuracy;
	}
	public void setAccuracy(int accuracy) {
		this.accuracy = accuracy;
	}

}
