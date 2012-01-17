package org.freejava.tools.handlers;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.List;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.google.common.io.Files;

public class SourceAttacherServiceSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

	public  static final String SERVICE = "http://javasourceattacher2.appspot.com";//http://localhost:8080
	//public  static final String SERVICE = "http://localhost:8080";//

	private boolean canceled = false;

    public SourceAttacherServiceSourceCodeFinder() {
    }

	public void cancel() {
		this.canceled = true;

	}


	public void find(String binFile, List<SourceFileResult> results) {
		File bin = new File(binFile);
		String url = null;
		String fileDownloaded = null;

        try {
	        if (canceled) return;
	        InputStream is2 = null;
	        String md5;
	        try {
				md5 = new String(Hex.encodeHex(Files.getDigest(bin, MessageDigest.getInstance("MD5"))));
	        	is2 = new URL(SERVICE + "/rest/libraries?md5=" + md5).openStream();
	        	String str = IOUtils.toString(is2);
	        	JSONArray json = JSONArray.fromObject(str);

		        for (int i = 0; i < json.size(); i++) {
			        if (canceled) return;
		        	JSONObject obj = (JSONObject) json.get(i);
		        	JSONObject source = obj.getJSONObject("source");
		        	if (source != null && !source.isNullObject()) {
		        		JSONArray ar = source.getJSONArray("urls");
		        		if (ar != null && !ar.isEmpty()) {
				        	String url1 = ar.getString(0);
				        	String tmpFile = download(url1);
				        	if (tmpFile != null && isSourceCodeFor(tmpFile, bin.getAbsolutePath())) {
				        		fileDownloaded = tmpFile;
				                url = url1;
				                break;
				            }
		        		}
		        	}
		        }

		        if (url != null && fileDownloaded != null) {
		        	String name = url.substring(url.lastIndexOf('/') + 1);
		    		results.add(new SourceFileResult(binFile, fileDownloaded, name, 90));
		        }

	        } finally {
	        	IOUtils.closeQuietly(is2);
	        }

        } catch (Exception e) {
			e.printStackTrace();
		}
    }
}
