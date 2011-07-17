package org.freejava.tools.handlers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class NexusSourceCodeFinder implements SourceCodeFinder {

	private boolean cancelled = false;

	public NexusSourceCodeFinder() {

	}

	@Override
    public void cancel() {
		this.cancelled = true;
    }

	@Override
    public void find(String binFile, String serviceUrl, List<?> results) {

        URL sourceFile = null;

        return sourceFile;
    }


    private static GAV getGAVOnSonatypeBySHA1(File binFile, String sha1) {
        GAV gav = null;
        try {
            URL url = new URL("http://repository.sonatype.org/service/local/data_index?sha1=" + sha1);
            URLConnection con = url.openConnection();
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Content-Type", "application/json");
            String json = IOUtils.toString(con.getInputStream());
            System.out.println(json);
            JSONObject jsonObject = JSONObject.fromObject(json);
            JSONArray data = jsonObject.getJSONArray("data");
            for (int i = 0; i < data.size(); i++) {
                JSONObject doci = data.getJSONObject(i);
                gav = new GAV();
                gav.setG(doci.getString("groupId"));
                gav.setA(doci.getString("artifectId"));
                gav.setV(doci.getString("version"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return gav;
    }

}
