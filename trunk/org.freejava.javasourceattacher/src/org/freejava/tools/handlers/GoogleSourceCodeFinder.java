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

public class GoogleSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

	private boolean canceled = false;
    private Tor tor;

    public GoogleSourceCodeFinder() {
    }

	public void cancel() {
		this.canceled = true;

	}

	private boolean checkCanceled() {
        boolean result = false;
		if (canceled) {
			// shutdown tor if needed
	        if (tor != null) {
	            tor.close();
	            tor = null;
	        }
	        result = true;
		}
        return result;
	}

	public void find(String binFile, List<SourceFileResult> results) {
		File bin = new File(binFile);
        String result = null;
        try {
	        String productName = parseProductName(FilenameUtils.getBaseName(bin.getName()));

	        if (checkCanceled()) return;

	        Set<String> fileNames = new HashSet<String>();
	        fileNames.add(bin.getName());

	        // If file doesn't contain a version, try to find more names from Google using MD5 signature
	        if (!Pattern.compile("([0-9]+)").matcher(bin.getName()).find()) {
		        Set<String> md5s = new HashSet<String>();
		        InputStream is = FileUtils.openInputStream(bin);
		        String md5;
		        try {
		            md5 = DigestUtils.md5Hex(is);
		            md5s.add(md5);
		        } finally {
		            IOUtils.closeQuietly(is);
		        }
		        if (checkCanceled()) return;
		        try {
		            Properties p = new Properties();
		            p.load(new URL("http://svn.codespot.com/a/eclipselabs.org/free-plugins/trunk/org.freejava.javasourceattacher/md5mapping.properties").openStream());
		            String altmd5 = p.getProperty(md5);
		            if (altmd5 != null) {
		                System.out.println("Alternative MD5:" + altmd5);
			            md5s.add(altmd5);
		            }
		        } catch (Exception e) {
		            // ignore
		        }
		        if (checkCanceled()) return;
		        fileNames.addAll(findFileNames(md5s, productName));
	        }

	        result = findSourceFile(fileNames, bin);
        } catch (Exception e) {
			e.printStackTrace();
		}

        if (checkCanceled()) return;

        if (result != null) {
        	String name = result.substring(result.lastIndexOf('/') + 1);
        	try {
	        	result = download(result);
	        	if (result != null && isSourceCodeFor(result, binFile)) {
	        		results.add(new SourceFileResult(binFile, result, name, 50));
	        	}
        	} catch (Exception e) {
				e.printStackTrace();
			}
        }


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

    private String findSourceFile(Collection<String> fileNames, File bin) throws Exception {
        String url1 = null;

        List<String> folderLinks = searchFolderLinks(fileNames);
        List<String> links = searchLinksInPages(folderLinks);
        for (Iterator<String> it = links.iterator(); it.hasNext();) {
            String link = it.next();
            boolean keep = false;
            for (String fileName : fileNames) {
                if (link.contains(FilenameUtils.getBaseName(fileName)) && (link.endsWith(".zip") || link.endsWith(".jar"))) {
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

                if (o1.contains("src") || o1.contains("sources")) i1 = 1;
                if (o2.contains("src") || o2.contains("sources")) i2 = 1;

                String patternStr = "[0-9\\_\\-\\.]+";
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
            if (isSourceCodeFor(file.getAbsolutePath(), bin.getAbsolutePath())) {
                url1 = url;
                break;
            } else {
                file.delete();
            }
        }
        return url1;
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
        parser.setFeature("http://xml.org/sax/features/namespaces", false);
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
        String q = "intitle:\"index of\" \"Parent Directory\"";

        List<String> namesq = new ArrayList<String>();
        for (String name : fileNames) {
            String base = FilenameUtils.getBaseName(name);
        	namesq.add(base + ".zip");
        }
    	String q1 = q;
        for (int i = 0; i <namesq.size(); i++) {
        	if (i == 0) {
        		q1 += " \"" + namesq.get(i) + "\"";
        	} else {
        		q1 += " OR \"" + namesq.get(i) + "\"";
        	}
        }
    	if (!q1.equals(q)) {
            URL url2 = new URL("http://www.google.com/search?hl=vi&source=hp&biw=&bih=&q=" + URLEncoder.encode(q1, "UTF-8"));
            System.out.println(url2.toString());
            String html = getString(url2);
            List<String> links = searchLinksInPage(url2.toString(), html);
            for (Iterator<String> it = links.iterator(); it.hasNext();) {
                if (!it.next().endsWith("/")) it.remove();
            }
            result.addAll(links);
    	}
        namesq.clear();
        for (String name : fileNames) {
            String base = FilenameUtils.getBaseName(name);
            if (!base.contains("src") && !base.contains("sources")) {
            	for (String sep : new String[]{"-", "_"}) {
                	for (String src : new String[]{"src", "sources"}) {
                    	for (String ext : new String[]{".jar", ".zip"}) {
                        	namesq.add(base + sep + src + ext);
                    	}
                	}
            	}
            } else {
            	namesq.add(base + ".jar");
            	namesq.add(base + ".zip");
            }
        }
    	q1 = q;
        for (int i = 0; i <namesq.size(); i++) {
        	if (i == 0) {
        		q1 += " \"" + namesq.get(i) + "\"";
        	} else {
        		q1 += " OR \"" + namesq.get(i) + "\"";
        	}
        }
    	if (!q1.equals(q)) {
            URL url2 = new URL("http://www.google.com/search?hl=vi&source=hp&biw=&bih=&q=" + URLEncoder.encode(q1, "UTF-8"));
            System.out.println(url2.toString());
            String html = getString(url2);
            List<String> links = searchLinksInPage(url2.toString(), html);
            for (Iterator<String> it = links.iterator(); it.hasNext();) {
                if (!it.next().endsWith("/")) it.remove();
            }
            result.addAll(links);
    	}

        return result;
    }

    private String getString(URL url) throws Exception {
    	System.out.println("getString: url:" + url.toString());

        String result = null;
        Exception exception = null;

        try {
            System.out.println("Will access URL via normal network");
            if (url.toString().contains("googleapis.com") || url.toString().contains("google.com")) {
            	System.out.println("Sleep 10s");
            	Thread.sleep(10000); // avoid google detection
            }
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
            e.printStackTrace();
        }
/*
        if (exception != null && !(exception instanceof FileNotFoundException)) {
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
        */
        return result;
    }

    private Set<String> findFileNames(Set<String> md5s, String productName) throws Exception {
        Set<String> result = new HashSet<String>();

        String md5str = null;
        for (String md5 : md5s) {
        	if (md5str == null) {
        		md5str = md5;
        	} else {
        		md5str += " OR " + md5;
        	}
        }

        // Guess real file name ([name]-[version].jar)
        URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&q=" + URLEncoder.encode(md5str, "UTF-8"));
        System.out.println(url.toString());
        String json = getString(url);
        System.out.println(json);
        List<String> links = getLinks(json);
        for (String link : links) {
            try {
                if (link.endsWith(".md5")) {
                    String md5FileName = link.substring(link.lastIndexOf('/')+1);
                    result.add(md5FileName.substring(0, md5FileName.length() - ".md5".length()));
                } else {//
                    String text = getString(new URL(link));
                    String patternStr = "[a-zA-Z][a-zA-Z0-9\\_\\-\\.]+\\.(jar|zip)";
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

        URL url2 = new URL("http://www.google.com/search?hl=vi&source=hp&biw=&bih=&q=" + URLEncoder.encode(md5str, "UTF-8"));
        System.out.println(url2.toString());
        String html = getString(url2);
        links =  searchLinksInPage(url2.toString(), html);
        for (String link : links) {
            if (link.endsWith(".md5")) {
                String md5FileName = link.substring(link.lastIndexOf('/')+1);
                result.add(md5FileName.substring(0, md5FileName.length() - ".md5".length()));
            }
        }

        String patternStr = "[a-zA-Z][a-zA-Z0-9\\_\\-\\.]+\\.(jar|zip)";
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
