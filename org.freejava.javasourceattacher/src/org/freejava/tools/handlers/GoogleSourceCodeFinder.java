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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.cyberneko.html.parsers.DOMParser;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.html.HTMLCollection;
import org.w3c.dom.html.HTMLDocument;
import org.xml.sax.InputSource;

public class GoogleSourceCodeFinder implements SourceCodeFinder {

    private static class NameVersion {
        private String name;
        private String version;
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getVersion() {
            return version;
        }
        public void setVersion(String version) {
            this.version = version;
        }
        public boolean equals(Object obj) {
            return EqualsBuilder.reflectionEquals(this, obj);
        }
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }
    }

    public File find(File bin) throws Exception {
        File result;

        List<NameVersion> nvs = new ArrayList<NameVersion>();
        NameVersion nv = parseNameVersion(FilenameUtils.getBaseName(bin.getName()));
        if (nv != null) nvs.add(nv);
        String name = nv != null ? nv.getName() : FilenameUtils.getBaseName(bin.getName());

        InputStream is = FileUtils.openInputStream(bin);
        String md5;
        try {
            md5 = DigestUtils.md5Hex(is);
        } finally {
            IOUtils.closeQuietly(is);
        }
        System.out.println("MD5:" + md5);

        List<NameVersion> nvs2 = findNameVersions(md5, name);
        nvs.addAll(nvs2);

        try {
            Properties p = new Properties();
            p.load(new URL("http://svn.codespot.com/a/eclipselabs.org/free-plugins/trunk/org.freejava.javasourceattacher/md5mapping.properties").openStream());
            String altmd5 = p.getProperty(md5);
            if (altmd5 != null) {
                System.out.println("Alternative MD5:" + altmd5);
                List<NameVersion> nvs3 = findNameVersions(altmd5, name);
                nvs.addAll(nvs3);
            }
        } catch (Exception e) {
            // ignore
        }

        for (NameVersion ver : nvs) {
            System.out.println("Name:" + ver.getName() + "; version: " + ver.getVersion());
        }

        result = downloadSourceFile(nvs);
        return result;
    }

    private static NameVersion parseNameVersion(String name) {
        NameVersion ns = null;
        Matcher m = Pattern.compile("([a-zA-Z\\-_]+)[\\-_]([0-9]+[0-9\\.]*[0-9]+)").matcher(name);
        if (m.find()) {
            ns = new NameVersion();
            ns.setName(m.group(1));
            ns.setVersion(m.group(2));
        } else {
            m = Pattern.compile("([a-zA-Z\\-_]+)[\\-_\\.]([0-9]+[0-9\\.]*[0-9]+)").matcher(name);
            if (m.find()) {
                ns = new NameVersion();
                ns.setName(m.group(1));
                ns.setVersion(m.group(2));
            }
        }
        return ns;
    }

    private static File downloadSourceFile(List<NameVersion> nvs) throws Exception {
        File result = null;

        List<String> folderLinks = searchFolderLinks(nvs);
        List<String> links = searchLinksInPages(folderLinks);
        for (Iterator<String> it = links.iterator(); it.hasNext();) {
            String link = it.next();
            boolean keep = false;
            for (NameVersion nv : nvs) {
                if (link.contains(nv.getName()) && link.contains(nv.getVersion()) && link.endsWith(".zip")) {
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
    private static List<String> searchLinksInPage(String url, String page) throws Exception {
        List<String> links = new ArrayList<String>();
        DOMParser parser = new DOMParser();
        parser.parse(new InputSource(new StringReader(page)));
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
    private static List<String> searchFolderLinks(List<NameVersion> nvs) throws Exception {
        List<String> result = new ArrayList<String>();
        for (NameVersion ns : nvs) {
            URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&"
                    + "q=" + URLEncoder.encode(ns.getName() + "-" + ns.getVersion() + "-src.zip intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
            System.out.println(url.toString());
            String json = IOUtils.toString(url.openStream());
            System.out.println(json);
            List<String> links = getLinks(json);
            if (links.isEmpty()) {
                url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&"
                        + "q=" + URLEncoder.encode(ns.getName() + "-" + ns.getVersion() + ".zip intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
                System.out.println(url.toString());
                json = IOUtils.toString(url.openStream());
                System.out.println(json);
                links = getLinks(json);
                if (links.isEmpty()) {
                    URL url2 = new URL("http://www.google.com/search?hl=vi&source=hp&biw=&bih=&q=" + URLEncoder.encode(ns.getName() + "-" + ns.getVersion() + ".zip intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
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

    private static List<NameVersion> findNameVersions(String md5, String name) throws Exception {
        List<NameVersion> result = new ArrayList<NameVersion>();

        // Guess real file name ([name]-[version].jar)
        URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&" + "q=" + md5);
        System.out.println(url.toString());
        String json = IOUtils.toString(url.openStream());
        System.out.println(json);
        List<String> links = getLinks(json);
        for (String link : links) {
            if (link.endsWith(".md5")) {
                String md5FileName = link.substring(link.lastIndexOf('/')+1);
                md5FileName = FilenameUtils.getBaseName(md5FileName);
                NameVersion nv = parseNameVersion(md5FileName);
                if (nv != null && !result.contains(nv)) {
                    result.add(nv);
                }
            } else {
                String text = IOUtils.toString(new URL(link).openStream());
                String patternStr = "[a-zA-Z][a-zA-Z0-9\\-\\.]+\\.jar";
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    String nm = matcher.group();
                    if (nm.contains(name)) {
                        NameVersion nv = parseNameVersion(nm);
                        if (nv != null && !result.contains(nv)) {
                            result.add(nv);
                        }
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
                md5FileName = FilenameUtils.getBaseName(md5FileName);
                NameVersion nv = parseNameVersion(md5FileName);
                if (nv != null && !result.contains(nv)) {
                    result.add(nv);
                }
            }
        }

        String patternStr = "[a-zA-Z][a-zA-Z0-9\\-\\.]+\\.jar";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String nm = matcher.group();
            if (nm.contains(name)) {
                NameVersion nv = parseNameVersion(nm);
                if (nv != null && !result.contains(nv)) {
                    result.add(nv);
                }
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
