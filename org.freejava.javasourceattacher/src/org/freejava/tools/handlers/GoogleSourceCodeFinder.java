package org.freejava.tools.handlers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.cyberneko.html.parsers.DOMParser;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.html.HTMLCollection;
import org.w3c.dom.html.HTMLDocument;
import org.xml.sax.InputSource;

public class GoogleSourceCodeFinder implements SourceCodeFinder {

    public File find(File bin) throws Exception {
        File result;

        String productName = parseProductName(FilenameUtils.getBaseName(bin.getName()));

        Set<String> fileNames = new HashSet<String>();
        fileNames.add(bin.getName());

        InputStream is = FileUtils.openInputStream(bin);
        String md5;
        try {
            md5 = DigestUtils.md5Hex(is);
        } finally {
            IOUtils.closeQuietly(is);
        }
        System.out.println("MD5:" + md5);

        fileNames.addAll(findFileNames(md5, productName));

        try {
            Properties p = new Properties();
            p.load(new URL("http://svn.codespot.com/a/eclipselabs.org/free-plugins/trunk/org.freejava.javasourceattacher/md5mapping.properties").openStream());
            String altmd5 = p.getProperty(md5);
            if (altmd5 != null) {
                System.out.println("Alternative MD5:" + altmd5);
                List<String> nvs3 = findFileNames(altmd5, productName);
                fileNames.addAll(nvs3);
            }
        } catch (Exception e) {
            // ignore
        }

        for (String fileName : fileNames) {
            System.out.println("Name:" + fileName);
        }

        result = downloadSourceFile(fileNames);
        return result;
    }

    private static String parseProductName(String name) {
        String ns = null;
        Matcher m = Pattern.compile("([a-zA-Z\\-_]+)[\\-_]([0-9]+[0-9\\.]*[0-9]+)").matcher(name);
        if (m.find()) {
            ns = m.group(1);
        } else {
            m = Pattern.compile("([a-zA-Z\\-_]+)[\\-_\\.]([0-9]+[0-9\\.]*[0-9]+)").matcher(name);
            if (m.find()) {
                ns = m.group(1);
            } else {
                ns = name;
            }
        }
        return ns;
    }

    private static File downloadSourceFile(Collection<String> fileNames) throws Exception {
        File result = null;

        List<String> folderLinks = searchFolderLinks(fileNames);
        List<String> links = searchLinksInPages(folderLinks);
        for (Iterator<String> it = links.iterator(); it.hasNext();) {
            String link = it.next();
            boolean keep = false;
            for (String fileName : fileNames) {
                if (link.contains(FilenameUtils.getBaseName(fileName)) && link.endsWith(".zip")) {
                    keep = true;
                }
            }
            if (!keep) {
                it.remove();
            }
        }

        Collections.sort(links, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return (o1.contains("-src") ? 0 : 1) - (o2.contains("-src") ? 0 : 1);
            }
        });

        if (!links.isEmpty()) {
            String url = links.get(0);
            String srcName = url.substring(url.lastIndexOf('/')+1);
            File file = new File(System.getProperty("user.home") + File.separatorChar
                    + ".sourceattacher"+ File.separatorChar + srcName);
            if (!file.exists()) {
                InputStream is = new URL(url).openStream();
                OutputStream os = new FileOutputStream(file);
                try {
                    IOUtils.copy(is, os);
                } finally {
                    IOUtils.closeQuietly(os);
                    IOUtils.closeQuietly(is);
                }
            }
            result = file;
        }
        return result;
    }

    private static List<String> searchLinksInPages(List<String> folderLinks) throws Exception {
        List<String> links = new ArrayList<String>();
        for (String url : folderLinks) {
            URL url2 = new URL(url);
            URLConnection con = url2.openConnection();
            con.setRequestProperty("User-Agent", "Mozilla 5.0 (Windows; U; " + "Windows NT 5.1; en-US; rv:1.8.0.11) ");
            String html = IOUtils.toString(con.getInputStream());
            links.addAll(searchLinksInPage(url, html));
        }
        return links;
    }
    private static List<String> searchLinksInPage(String url, String html) throws Exception {
        List<String> links = new ArrayList<String>();
        DOMParser parser = new DOMParser();
        parser.parse(new InputSource(new StringReader(html)));
        HTMLDocument document = (HTMLDocument) parser.getDocument();
        HTMLCollection pagelinks = document.getLinks();
        for (int ii = 0; ii < pagelinks.getLength(); ii++) {
            Node link = pagelinks.item(ii);
            NamedNodeMap attrs = link.getAttributes();
            String href = null;
            for (int j = 0; j < attrs.getLength(); j++) {
                if ("href".equalsIgnoreCase(attrs.item(j).getNodeName())) {
                    href = attrs.item(j).getNodeValue();
                }
            }
            if (!href.startsWith("javascript:") && !href.startsWith("news:")) {
                String absHref = new URL(new URL(url), href).toString();
                int lastSep = absHref.lastIndexOf('/');
                int lastSharp = absHref.lastIndexOf('#');
                if (lastSep >= 0 && lastSharp >= 0 && lastSharp > lastSep) {
                    absHref = absHref.substring(0, lastSharp);
                }
                links.add(absHref);
            }
        }

        return links;
    }
    private static List<String> searchFolderLinks(Collection<String> fileNames) throws Exception {
        List<String> result = new ArrayList<String>();
        for (String fileName : fileNames) {
            String base = FilenameUtils.getBaseName(fileName);
            URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&"
                    + "q=" + URLEncoder.encode(base + "-src.zip intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
            System.out.println(url.toString());
            String json = IOUtils.toString(url.openStream());
            System.out.println(json);
            List<String> links = getLinks(json);
            if (links.isEmpty()) {
                url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&"
                        + "q=" + URLEncoder.encode(base + ".zip intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
                System.out.println(url.toString());
                json = IOUtils.toString(url.openStream());
                System.out.println(json);
                links = getLinks(json);
                if (links.isEmpty()) {
                    URL url2 = new URL("http://www.google.com/search?hl=vi&source=hp&biw=&bih=&q=" + URLEncoder.encode(base + ".zip intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
                    System.out.println(url2.toString());
                    URLConnection con = url2.openConnection();
                    con.setRequestProperty("User-Agent", "Mozilla 5.0 (Windows; U; " + "Windows NT 5.1; en-US; rv:1.8.0.11) ");
                    String html = IOUtils.toString(con.getInputStream());
                    links = searchLinksInPage(url2.toString(), html);
                    for (Iterator<String> it = links.iterator(); it.hasNext();) {
                        if (!it.next().endsWith("/")) it.remove();
                    }
                }
            }
            result.addAll(links);
        }

        return result;
    }

    private static List<String> findFileNames(String md5, String productName) throws Exception {
        List<String> result = new ArrayList<String>();

        // Guess real file name ([name]-[version].jar)
        URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&" + "q=" + md5);
        System.out.println(url.toString());
        String json = IOUtils.toString(url.openStream());
        System.out.println(json);
        List<String> links = getLinks(json);
        for (String link : links) {
            if (link.endsWith(".md5")) {
                String md5FileName = link.substring(link.lastIndexOf('/')+1);
                result.add(md5FileName.substring(0, md5FileName.length() - ".md5".length()));
            } else {
                String text = IOUtils.toString(new URL(link).openStream());
                String patternStr = "[a-zA-Z][a-zA-Z0-9\\-\\.]+\\.jar";
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    String nm = matcher.group();
                    if (nm.contains(productName)) {
                        result.add(nm);
                    }
                }
            }
        }

        URL url2 = new URL("http://www.google.com/search?hl=vi&source=hp&biw=&bih=&q=" + md5);
        System.out.println(url2.toString());
        URLConnection con = url2.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla 5.0 (Windows; U; " + "Windows NT 5.1; en-US; rv:1.8.0.11) ");
        String html = IOUtils.toString(con.getInputStream());
        links =  searchLinksInPage(url2.toString(), html);
        for (String link : links) {
            if (link.endsWith(".md5")) {
                String md5FileName = link.substring(link.lastIndexOf('/')+1);
                result.add(md5FileName.substring(0, md5FileName.length() - ".md5".length()));
            }
        }

        String patternStr = "[a-zA-Z][a-zA-Z0-9\\-\\.]+\\.jar";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String nm = matcher.group();
            if (nm.contains(productName)) {
                result.add(nm);
            }
        }
        return result;
    }

    private static List<String> getLinks(String json) {
        List<String> result = new ArrayList<String>();
        JSONObject jsonObject = JSONObject.fromObject(json);
        if (jsonObject.getInt("responseStatus") == 200) {
            JSONObject responseData = jsonObject.getJSONObject("responseData");
            JSONArray array = responseData.getJSONArray("results");
            for (int i = 0; i < array.size(); i ++) {
                JSONObject obj = array.getJSONObject(i);
                result.add(obj.getString("url"));
            }
        }
        return result;
    }

}
