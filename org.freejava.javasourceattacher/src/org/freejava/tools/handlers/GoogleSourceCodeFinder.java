package org.freejava.tools.handlers;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.cyberneko.html.parsers.DOMParser;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.html.HTMLCollection;
import org.w3c.dom.html.HTMLDocument;

public class GoogleSourceCodeFinder implements SourceCodeFinder {

    @Override
    public File find(File bin) throws Exception {
        File result;
        String name = findRealName(bin);
        result = downloadSourceFile(name);
        return result;
    }

    private static File downloadSourceFile(String name) throws Exception {
        File result = null;

        String srcName = name.substring(0, name.length() - ".jar".length()) + "-src.zip";
        String srcName2 = name.substring(0, name.length() - ".jar".length()) + ".zip";
        File file = new File(System.getProperty("user.home") + File.separatorChar
                + ".sourceattacher"+ File.separatorChar + srcName);
        if (file.exists()) {
            result = file;
        } else {
            URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&"
                    + "q=" + URLEncoder.encode(srcName + " intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
            String json = IOUtils.toString(url.openStream());
            System.out.println(json);
            if (emptySearch(json)) {
                url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&"
                        + "q=" + URLEncoder.encode(srcName2 + " intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
                json = IOUtils.toString(url.openStream());
                System.out.println(json);
            }
            JSONObject jsonObject = JSONObject.fromObject(json);
            if (jsonObject.getInt("responseStatus") == 200) {
                JSONObject responseData = jsonObject.getJSONObject("responseData");
                JSONArray array = responseData.getJSONArray("results");
                for (int i = 0; i < array.size(); i ++) {
                    JSONObject obj = array.getJSONObject(i);
                    URL url2 = new URL(obj.getString("url"));
                    URL srcUrl = new URL(url2, srcName);

                    HttpURLConnection con = (HttpURLConnection) srcUrl.openConnection();

                    con.setRequestMethod("HEAD");
                    if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        DOMParser parser = new DOMParser();
                        parser.parse(url2.toString());
                        HTMLDocument document = (HTMLDocument) parser.getDocument();
                        HTMLCollection links = document.getLinks();
                        for (int ii = 0; ii < links.getLength(); ii++) {
                            Node link = links.item(ii);
                            NamedNodeMap attrs = link.getAttributes();
                            String href = null;
                            for (int j = 0; j < attrs.getLength(); j++) {
                                if ("href".equalsIgnoreCase(attrs.item(j).getNodeName())) {
                                    href = attrs.item(j).getNodeValue();
                                }
                            }
                            if (!href.startsWith("javascript:") && !href.startsWith("news:")) {
                                String absHref = new URL(url2, href).toString();
                                int lastSep = absHref.lastIndexOf('/');
                                int lastSharp = absHref.lastIndexOf('#');
                                if (lastSep >= 0 && lastSharp >= 0 && lastSharp > lastSep) {
                                    absHref = absHref.substring(0, lastSharp);
                                }
                                if (absHref.endsWith(".zip")) {
                                    String urlFile = absHref.substring(absHref.lastIndexOf('/'));
                                    Pattern pattern = Pattern.compile("[a-zA-Z]+");
                                    Matcher matcher = pattern.matcher(name);
                                    if (matcher.find()) {
                                        String nm = matcher.group();
                                        if (urlFile.contains(nm) && urlFile.contains("src")) {
                                            srcUrl = new URL(absHref);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    byte[] data = IOUtils.toByteArray(srcUrl.openStream());
                    FileUtils.writeByteArrayToFile(file, data);
                    result = file;
                    break;
                }
            }
        }
        return result;
    }

    private static boolean emptySearch(String json) {
        boolean result = true;
        JSONObject jsonObject = JSONObject.fromObject(json);
        if (jsonObject.getInt("responseStatus") == 200) {
            JSONObject responseData = jsonObject.getJSONObject("responseData");
            JSONArray array = responseData.getJSONArray("results");
            result =  array.size() == 0;
        }
        return result;
    }

    private static String findRealName(File binFile) throws Exception {
        String name = null;

        // If the file name doesn't have version number, we need to google for a better name
        if (!Pattern.compile("\\-[0-9]+").matcher(binFile.getName()).find()) {
            // Guess real file name ([name]-[version].jar)
            String md5 = DigestUtils.md5Hex(FileUtils.openInputStream(binFile));
            System.out.println("MD5:" + md5);
            URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&" + "q=" + md5);
            String json = IOUtils.toString(url.openStream());
            System.out.println(json);
            Map<String, Integer> counter = new HashMap<String, Integer>();
            String patternStr = "[a-zA-Z][a-zA-Z0-9\\-\\.]+\\.jar";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(json);
            while (matcher.find()) {
                String nm = matcher.group();
                Integer count;
                if (counter.containsKey(nm)) {
                    count = new Integer(1 + counter.get(nm).intValue());
                } else {
                    count = new Integer(1);
                }
                counter.put(nm, count);
            }
            Integer max = new Integer(0);
            for (Map.Entry<String, Integer> entry : counter.entrySet()) {
                if (entry.getValue().compareTo(max) > 0) {
                    name = entry.getKey();
                }
            }

            if (name == null) {
                URL url2 = new URL("http://www.google.com/search?hl=vi&source=hp&biw=&bih=&q=" + md5);
                URLConnection con = url2.openConnection();
                con.setRequestProperty("User-Agent", "Mozilla 5.0 (Windows; U; "
                        + "Windows NT 5.1; en-US; rv:1.8.0.11) ");
                String html = IOUtils.toString(con.getInputStream());
                counter = new HashMap<String, Integer>();
                patternStr = "/([a-zA-Z\\.\\-\\_0-9]+)\\.md5";
                pattern = Pattern.compile(patternStr);
                matcher = pattern.matcher(html);
                while (matcher.find()) {
                    String nm = matcher.group(1);
                    Integer count;
                    if (counter.containsKey(nm)) {
                        count = new Integer(1 + counter.get(nm).intValue());
                    } else {
                        count = new Integer(1);
                    }
                    counter.put(nm, count);
                }
                max = new Integer(0);
                for (Map.Entry<String, Integer> entry : counter.entrySet()) {
                    if (entry.getValue().compareTo(max) > 0) {
                        name = entry.getKey();
                    }
                }
            }
        }

        // Not found on the web, use name of binFile
        if (name == null) {
            name = binFile.getName();
        }

        return name;
    }

}
