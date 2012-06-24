package org.freejava.tools.handlers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.freejava.tools.handlers.classpathutil.Logger;

import com.google.common.io.Files;

public class WebBasedArtifactorySourceCodeFinder extends ArtifactorySourceCodeFinder {

	private String serviceUrl;

	public WebBasedArtifactorySourceCodeFinder(String serviceUrl) {
		super(serviceUrl);
		this.serviceUrl = serviceUrl;
	}

	@Override
	public void find(String binFile, List<SourceFileResult> results) {
		// below code using Windows Scripting Host so stop if not windows OS
		if (!SystemUtils.IS_OS_WINDOWS) return;

		try {
			String checksumsearchUrl = serviceUrl.substring(0, serviceUrl.indexOf("/webapp/")) + "/webapp/checksumsearch.html";
			String gavsearchUrl = serviceUrl.substring(0, serviceUrl.indexOf("/webapp/")) + "/webapp/gavcsearch.html";
			String sha1 = new String(Hex.encodeHex(Files.getDigest(new File(binFile), MessageDigest.getInstance("SHA"))));

			InputStream is = this.getClass().getResourceAsStream("WebBasedArtifactorySourceCodeFinder.js");
			File scriptFile = File.createTempFile("WebBasedArtifactorySourceCodeFinder", ".js");
			OutputStream os = new FileOutputStream(scriptFile);
			IOUtils.copy(is, os);
			IOUtils.closeQuietly(is);
			IOUtils.closeQuietly(os);

			Process child = Runtime.getRuntime().exec(new String[]{"cscript.exe", scriptFile.getAbsolutePath(), "//Nologo", checksumsearchUrl, gavsearchUrl, sha1});
		    InputStream in = child.getInputStream();
		    String output = "";
		    int c;
		    while ((c = in.read()) != -1) {
		        output += (char)c;
		    }
		    in.close();

		    scriptFile.delete();

		    String url = StringUtils.trim(output);
		    if (StringUtils.isNotEmpty(url)) {
				String name = url.substring(url.lastIndexOf('/')+1);
        		String result = download(url);
	        	if (result != null && isSourceCodeFor(result, binFile)) {
	        		SourceFileResult object = new SourceFileResult(binFile, result, name, 100);
	        		Logger.debug(this.toString() + " FOUND: " + object, null);
	        		results.add(object);
	        	}
		    }
		} catch (Exception e) {
            e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		WebBasedArtifactorySourceCodeFinder m = new WebBasedArtifactorySourceCodeFinder("http://repo.springsource.org/webapp/home.html");
		String binFile = "c:/Users/thaiha/.m2/repository/commons-cli/commons-cli/1.2/commons-cli-1.2.jar";
		List<SourceFileResult> results = new ArrayList<SourceFileResult>();
		m.find(binFile, results);

	}

}
