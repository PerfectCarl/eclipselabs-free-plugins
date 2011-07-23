package org.freejava.tools.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class MavenRepoSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

	private boolean canceled = false;

	@Override
	public void cancel() {
		this.canceled = true;

	}

	@Override
	public void find(String binFile, List<SourceFileResult> results) {
        Collection<GAV> gavs = new HashSet<GAV>();
		try {
	        FileInputStream fis = FileUtils.openInputStream(new File(binFile));
	        String sha1 = DigestUtils.shaHex(fis);
	        IOUtils.closeQuietly(fis);
	        gavs.addAll(findArtifactsUsingMavenCentral(sha1));
        } catch (Exception e) {
            e.printStackTrace();
        }

		if (canceled) return;

		try {
			gavs.addAll(findGAVFromFile(binFile));
        } catch (Exception e) {
            e.printStackTrace();
        }

		if (canceled) return;

		Collection<String> sourcesUrls = new HashSet<String>();
		try {
			sourcesUrls.addAll(findSourcesUsingMavenCentral(gavs));
        } catch (Exception e) {
            e.printStackTrace();
        }

		for (String url : sourcesUrls) {
        	String result = download(url);
        	if (isSourceCodeFor(result, binFile)) {
        		results.add(new SourceFileResult(binFile, result, 100));
        	}
		}
    }

    private Collection<String> findSourcesUsingMavenCentral(Collection<GAV> gavs) throws Exception {
		Collection<String> results = new HashSet<String>();
        for (GAV gav : gavs) {
        	if (canceled) return results;

        	//g:"ggg" AND a:"aaa" AND v:"vvv" AND l:"sources"
        	String qVal = "g:\"" + gav.getG() + "\" AND a:\"" + gav.getA()
        			+ "\" AND v:\"" + gav.getV() + "\" AND l:\"sources\"";
        	String url = "http://search.maven.org/solrsearch/select?q=" + URLEncoder.encode(qVal, "UTF-8") + "&rows=20&wt=json";
            String json = IOUtils.toString(new URL(url).openStream());
            JSONObject jsonObject = JSONObject.fromObject(json);
            JSONObject response = jsonObject.getJSONObject("response");

            for (int i = 0; i < response.getInt("numFound"); i++) {
                JSONArray docs = response.getJSONArray("docs");
                JSONObject doci = docs.getJSONObject(i);
                String g = doci.getString("g");
                String a = doci.getString("a");
                String v = doci.getString("v");
                JSONArray array = doci.getJSONArray("ec");
                if (array.contains("-sources.jar")) {
	                String path = g.replace('.', '/') + '/' + a + '/' + v + '/' + a + '-' + v + "-sources.jar";
	                path = "http://search.maven.org/remotecontent?filepath=" + path;
	                results.add(path);
                }
            }
        }

        return results;
	}

	private Collection<GAV> findArtifactsUsingMavenCentral(String sha1) throws Exception {
        Set<GAV> results = new HashSet<GAV>();
        String json = IOUtils.toString(new URL("http://search.maven.org/solrsearch/select?q=" + URLEncoder.encode("1:\"" + sha1 + "\"", "UTF-8") + "&rows=20&wt=json").openStream());
        JSONObject jsonObject = JSONObject.fromObject(json);
        JSONObject response = jsonObject.getJSONObject("response");

        for (int i = 0; i < response.getInt("numFound"); i++) {
            JSONArray docs = response.getJSONArray("docs");
            JSONObject doci = docs.getJSONObject(i);
            GAV gav = new GAV();
            gav.setG(doci.getString("g"));
            gav.setA(doci.getString("a"));
            gav.setV(doci.getString("v"));
            results.add(gav);
        }
		return results;
	}
/*
	private static File download(GAV srcinfo) throws Exception {
        File result = null;
        String groupId = srcinfo.getG();
        String artifactId = srcinfo.getA();
        String version = srcinfo.getV();

        String url = "http://repo1.maven.org/maven2/"
            + groupId.replace('.', '/') + "/"
            + artifactId + "/"
            + version + "/"
            + artifactId + "-" + version + "-sources.jar";
        URL srcUrl = new URL(url);
        HttpURLConnection con = (HttpURLConnection) srcUrl.openConnection();
        con.setRequestMethod("HEAD");
        if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
            result = new File(download(url));
        }

        return result;
    }
    */
}
