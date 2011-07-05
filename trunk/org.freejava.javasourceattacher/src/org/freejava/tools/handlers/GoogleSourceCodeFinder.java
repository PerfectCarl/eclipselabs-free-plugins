package org.freejava.tools.handlers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.cyberneko.html.parsers.DOMParser;
import org.eclipse.core.runtime.IProgressMonitor;
import org.silvertunnel.netlib.adapter.url.NetlibURLStreamHandlerFactory;
import org.silvertunnel.netlib.api.NetFactory;
import org.silvertunnel.netlib.api.NetLayer;
import org.silvertunnel.netlib.api.NetLayerIDs;
import org.silvertunnel.netlib.layer.tor.TorNetLayer;
import org.silvertunnel.netlib.layer.tor.clientimpl.Tor;
import org.silvertunnel.netlib.layer.tor.common.TorConfig;
import org.silvertunnel.netlib.util.TempfileStringStorage;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.html.HTMLCollection;
import org.w3c.dom.html.HTMLDocument;
import org.xml.sax.InputSource;

public class GoogleSourceCodeFinder implements SourceCodeFinder {

    private IProgressMonitor monitor;
    private Tor tor;

    public GoogleSourceCodeFinder(IProgressMonitor monitor) {
        this.monitor = monitor;
    }

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

        result = downloadSourceFile(fileNames, bin);

        // shutdown tor if needed
        if (tor != null) {
            tor.close();
            tor = null;
        }

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

    private File downloadSourceFile(Collection<String> fileNames, File bin) throws Exception {
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
                int i1 = 0, i2 = 0;

                if (o1.contains("-src")) i1 = 1;
                if (o2.contains("-src")) i2 = 1;

                String patternStr = "[0-9\\-\\.]+";
                Pattern pattern = Pattern.compile(patternStr);
                int verlength1 = 0, verlength2 = 0;
                Matcher matcher = pattern.matcher(o1);
                if (matcher.find()) verlength1 = matcher.group(0).length();
                matcher = pattern.matcher(o2);
                if (matcher.find()) verlength2 = matcher.group(0).length();
                if (verlength1 > verlength2) i1++;
                if (verlength2 > verlength1) i2++;

                return (i2 - i1);
            }
        });

        for (String url : links) {
            String srcName = url.substring(url.lastIndexOf('/') + 1);
            File cacheDir = new File(System.getProperty("user.home") + File.separatorChar + ".sourceattacher");
            File file = new File(cacheDir, srcName);
            if (!file.exists()) {
                if (!cacheDir.exists()) cacheDir.mkdirs();
                InputStream is = new URL(url).openStream();
                OutputStream os = new FileOutputStream(file);
                try {
                    IOUtils.copy(is, os);
                } finally {
                    IOUtils.closeQuietly(os);
                    IOUtils.closeQuietly(is);
                }
            }
            if (isSourceCodeFor(file, bin)) {
                result = file;
                break;
            } else {
                file.delete();
            }
        }
        return result;
    }

    private static boolean isSourceCodeFor(File src, File bin) throws Exception {
        boolean result = false;

        List<String> binList = new ArrayList<String>();
        ZipFile zf = new ZipFile(bin);
        for (Enumeration entries = zf.entries(); entries.hasMoreElements();) {
            String zipEntryName = ((ZipEntry)entries.nextElement()).getName();
            binList.add(zipEntryName);
        }

        zf = new ZipFile(src);
        for (Enumeration entries = zf.entries(); entries.hasMoreElements();) {
            String zipEntryName = ((ZipEntry)entries.nextElement()).getName();
            String fileBaseName = FilenameUtils.getBaseName(zipEntryName);
            String fileExt = FilenameUtils.getExtension(zipEntryName);
            if ("java".equals(fileExt) && fileBaseName != null) {
                for (String zipEntryName2 : binList) {
                    String fileBaseName2 = FilenameUtils.getBaseName(zipEntryName2);
                    String fileExt2 = FilenameUtils.getExtension(zipEntryName2);
                    if ("class".equals(fileExt2) && fileBaseName.equals(fileBaseName2)) {
                        result = true;
                        return result;
                    }
                }
            }
            binList.add(zipEntryName);
        }

        return result;
    }

    private List<String> searchLinksInPages(List<String> folderLinks) throws Exception {
        List<String> links = new ArrayList<String>();
        for (String url : folderLinks) {
            URL url2 = new URL(url);
            String html = getString(url2);
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
    private List<String> searchFolderLinks(Collection<String> fileNames) throws Exception {
        List<String> result = new ArrayList<String>();
        for (String fileName : fileNames) {
            String base = FilenameUtils.getBaseName(fileName);
            URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&"
                    + "q=" + URLEncoder.encode(base + "-src.zip intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
            System.out.println(url.toString());
            String json = getString(url);
            System.out.println(json);
            List<String> links = getLinks(json);
            if (links.isEmpty()) {
                url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&"
                        + "q=" + URLEncoder.encode(base + ".zip intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
                System.out.println(url.toString());
                json = getString(url);
                System.out.println(json);
                links = getLinks(json);
                if (links.isEmpty()) {
                    URL url2 = new URL("http://www.google.com/search?hl=vi&source=hp&biw=&bih=&q=" + URLEncoder.encode(base + ".zip intitle:\"index of\" \"Parent Directory\"", "UTF-8"));
                    System.out.println(url2.toString());
                    String html = getString(url2);
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

    private String getString(URL url) throws Exception {

        if (monitor != null && monitor.isCanceled()) return null;

        String result = null;
        Exception exception = null;

        try {
            System.out.println("Will access URL via normal network");
            URLConnection con = url.openConnection();
            con.setRequestProperty("User-Agent", "Mozilla 5.0 (Windows; U; " + "Windows NT 5.1; en-US; rv:1.8.0.11) ");
            InputStream is = null;
            try {
                is = con.getInputStream();
                result = IOUtils.toString(is);
            } finally {
                IOUtils.closeQuietly(is);
            }
        } catch (Exception e) {
            exception = e;
        }

        if (exception != null) {
            int error = 0;
            while (error < 2) {
                NetLayer lowerNetLayer = null;
                try {
                    System.out.println("Will access URL via TOR network");
                    if (lowerNetLayer == null) {
                        // create a new netLayer instance
                        TorConfig.routeMinLength = TorConfig.routeMaxLength = 1;
                        TorConfig.routeUniqueClassC = TorConfig.routeUniqueCountry = false;
                        TorConfig.minimumIdleCircuits = 1;

                        NetLayer tcpipNetLayer = NetFactory.getInstance().getNetLayerById(NetLayerIDs.TCPIP);
                        NetLayer tlsNetLayer = NetFactory.getInstance().getNetLayerById(NetLayerIDs.TLS_OVER_TCPIP);
                        tor = new Tor(tlsNetLayer, tcpipNetLayer, TempfileStringStorage.getInstance());
                        lowerNetLayer = new TorNetLayer(tor);
                    }
                    lowerNetLayer.waitUntilReady();
                    NetlibURLStreamHandlerFactory factory = new NetlibURLStreamHandlerFactory(false);
                    factory.setNetLayerForHttpHttpsFtp(lowerNetLayer);
                    URLStreamHandler handler = factory.createURLStreamHandler(url.getProtocol());
                    URL context = null;
                    URL url2 = new URL(context, url.toExternalForm(), handler);
                    URLConnection con = url2.openConnection();
                    InputStream is = null;
                    try {
                        is = con.getInputStream();
                        result = IOUtils.toString(is);
                    } finally {
                        IOUtils.closeQuietly(is);
                    }
                    break;
                } catch (Exception e2) {
                    error ++;
                    if (e2 instanceof FileNotFoundException) {
                        error = 2;
                        break;
                    }
                    e2.printStackTrace();
                    lowerNetLayer.clear();
                }
            }

            if (error == 2) throw exception;
        }
        return result;
    }

    private List<String> findFileNames(String md5, String productName) throws Exception {
        List<String> result = new ArrayList<String>();

        // Guess real file name ([name]-[version].jar)
        URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&" + "q=" + md5);
        System.out.println(url.toString());
        String json = getString(url);
        System.out.println(json);
        List<String> links = getLinks(json);
        for (String link : links) {
            try {
                if (link.endsWith(".md5")) {
                    String md5FileName = link.substring(link.lastIndexOf('/')+1);
                    result.add(md5FileName.substring(0, md5FileName.length() - ".md5".length()));
                } else {
                    String text = getString(new URL(link));
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
            } catch (Exception e) {
                // Ignore it
            }
        }

        URL url2 = new URL("http://www.google.com/search?hl=vi&source=hp&biw=&bih=&q=" + md5);
        System.out.println(url2.toString());
        String html = getString(url2);
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
