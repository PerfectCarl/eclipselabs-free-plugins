package org.freejava.tools.handlers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class MavenRepoSourceCodeFinder implements SourceCodeFinder {

    @Override
    public File find(File bin) throws Exception {
        File sourceFile = null;

        String sha1 = DigestUtils.shaHex(FileUtils.openInputStream(bin));
        System.out.println("SHA1:" + sha1);

        GAV gav = getGAVOnCentralBySHA1(bin, sha1);

        if (gav != null) {
            System.out.println("g:" + gav.getG() + "; a: " + gav.getA() + "; v:" + gav.getV());
            sourceFile = download(gav);
        }

        return sourceFile;
    }

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
            result = download(srcUrl);
        }

        return result;
    }

    private static File download(URL url) throws Exception {
        String urlStr = url.toURI().toString();
        String fileName = urlStr.substring(urlStr.lastIndexOf('/')+ 1);
        File file = new File(System.getProperty("user.home") + File.separatorChar
                + ".sourceattacher"+ File.separatorChar + fileName);
        if (!file.exists()) {
            OutputStream os = new FileOutputStream(file);
            try {
                IOUtils.copy(url.openStream(), os);
            } finally {
                IOUtils.closeQuietly(os);
            }
        }
        return file;

    }

    private static GAV getGAVOnCentralBySHA1(File binFile, String sha1) {
        GAV gav = null;
        try {
            String json = IOUtils.toString(new URL("http://search.maven.org/solrsearch/select?q=" + URLEncoder.encode("1:\"" + sha1 + "\"", "UTF-8") + "&rows=20&wt=json").openStream());
            System.out.println(json);
            JSONObject jsonObject = JSONObject.fromObject(json);
            JSONObject response = jsonObject.getJSONObject("response");
            for (int i = 0; i < response.getInt("numFound"); i++) {
                JSONArray docs = response.getJSONArray("docs");
                JSONObject doci = docs.getJSONObject(i);
                boolean hasSource = false;
                JSONArray ec = doci.getJSONArray("ec");
                for (int j = 0; j < ec.size(); j++) {
                    if ("sources.jar".equals(ec.getString(j))) {
                        hasSource  = true;
                        break;
                    }
                }
                if (!hasSource) continue;

                gav = new GAV();
                gav.setRepositoryUrl("http://repo1.maven.org/maven2/");
                gav.setG(doci.getString("g"));
                gav.setA(doci.getString("a"));
                gav.setV(doci.getString("v"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return gav;
    }
/*
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
*/
}
