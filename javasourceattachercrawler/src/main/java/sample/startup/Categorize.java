package sample.startup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.thoughtworks.xstream.XStream;

public class Categorize {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {


		boolean exception;
		Set<String> donePairs = new HashSet<String>();
		do {
			exception = false;
			try {

				State state = loadState();

				Map<String, Link> links = state.getLinks();
				if (state.getPairsProcessed() != null) {
					donePairs = state.getPairsProcessed();
				}

				Map<String, List<String>> groups = buildGroups(links);
				Map<String, String> binUrl2SourceUrl = buildBinUrl2SourceUrlMap(groups);

				final Map<String, Double> sizes = loadURLSize(links);
				List<String> sortedBins = sortBinBySize(binUrl2SourceUrl, sizes);

				Map<String, Link> infos = getInfo(binUrl2SourceUrl, sortedBins, links, donePairs);

				saveToXML(infos, donePairs);

			} catch (Exception e) {
				System.out.println("EXCEPTION FOUND. RESTARTING..");
				e.printStackTrace();
				exception = true;
			}
		} while (exception);

	}

	private static Map<String, Link> getInfo(Map<String, String> binUrl2SourceUrl,
			List<String> sortedBins, Map<String, Link> links, Set<String> donePairs) throws Exception {
		Map<String, Link> result = new HashMap<String, Link>(links);

		// Process bin Urls in order
		DefaultHttpClient httpclient = new DefaultHttpClient();
		for (int i = 0; i < sortedBins.size(); i++) {
			String bin = sortedBins.get(i);
			String src = binUrl2SourceUrl.get(bin);

			// avoid duplicate
			String pair = DigestUtils.md5Hex(bin + src);
			if (donePairs.contains(pair)) continue;

			// Debug and saving point
			System.out.println("\nsrc: "+src.substring("http://archive.apache.org/dist/".length())+";     bin:"+bin.substring("http://archive.apache.org/dist/".length()) );
			System.out.println("percent=" + i +"/" + sortedBins.size() + "=" + (int)((double)i*100/sortedBins.size()));
			if (i % 10 == 0 && i > 0) {
				saveToXML(result, donePairs);
			}

			// source file
			Link info1 = result.get(src);
	        if (info1 == null || info1.getMd5() == null || info1.getNames() == null) {
	        	info1 = getFileInfo(httpclient, src, result);
	        	result.put(src, info1);
	        }
	        boolean isJavaSourceFile = false;
	        List<String> javanames = info1.getNames();
	        for (String name : javanames) {
	        	if (name.endsWith(".java")) {
	        		isJavaSourceFile = true;
	        		break;
	        	}
	        }

        	// binary file
	        if (isJavaSourceFile) {
	        	processBinFile(result, httpclient, bin, src, javanames);
	        }

	        // avoid duplicate
        	donePairs.add(pair);
		}

		return result;
	}

	private static void processBinFile(Map<String, Link> result,
			DefaultHttpClient httpclient, String bin, String src,
			List<String> javanames) throws Exception {

		// jar file
		if (bin.endsWith(".jar")) {
			Link info = result.get(bin);
			if (info == null || info.getMd5() == null || info.getNames() == null) {
		    	info = getFileInfo(httpclient, bin, result);
		    	result.put(bin, info);
		        List<String> classnames = info.getNames();
		        boolean valid = isSource(javanames, classnames);
		        if (valid) {
		        	info.setSrc(src);
		        	result.put(bin, info);
		        	System.out.println("->bin:" + bin.substring("http://archive.apache.org/dist/".length()) + "; src:" + src.substring("http://archive.apache.org/dist/".length()));
		        }
		    }

		} else { // zip file


			File file = null;
			ZipFile zf = null;
			try {
				file = download(httpclient, bin);
				zf = new ZipFile(file);

				Enumeration<ZipArchiveEntry> entries = zf.getEntries();
				while (entries.hasMoreElements()) {
					ZipArchiveEntry entry = entries.nextElement();
					if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".jar")) {

						// Copy inner jar file to temp file
						File temp = File.createTempFile("tmp", ".jar");
					    OutputStream fos = Files.newOutputStreamSupplier(temp).getOutput();
					    InputStream zis = zf.getInputStream(entry);
					    IOUtils.copy(zis, fos);
						IOUtils.closeQuietly(zis);
						IOUtils.closeQuietly(fos);
						// process jar files in temp file
						try {
							processJarFile(result, src, javanames, "jar:" + bin + "!/" + entry.getName(), temp);
						} catch (Exception e) {
							System.out.println("IGNORED:" + e);
						}
						// remove temp file
						temp.delete();
					}
				}
			} finally {
				ZipFile.closeQuietly(zf);
				if (file != null) file.delete();
			}
		}
	}

	private static void saveToXML(Map<String, Link> links, Set<String> pairsProcessed) throws IOException {
		XStream xstream = new XStream();
		xstream.alias("state", State.class);
		xstream.alias("link", Link.class);
		State state = new State();
		state.setLinks(links);
		state.setPairsProcessed(pairsProcessed);

		String out = xstream.toXML(state);
		System.out.println("SAVING...");

		Files.write(out, new File("urls2.xml"), Charset.forName("UTF-8"));
	}

	private static void processJarFile(
			Map<String, Link> result,
			String src, List<String> javanames,
			String path, File temp) throws Exception {

		System.out.println("processJarFiles: " + path + " " + temp);

		// Get class file names
		List<String> classnames = new ArrayList<String>();
		ZipFile zf = new ZipFile(temp);
		Enumeration<ZipArchiveEntry> entries = zf.getEntries();
		for (; entries.hasMoreElements(); ) {
			ZipArchiveEntry entry = entries.nextElement();
			String entryName = entry.getName();
			if (entryName.endsWith(".class") || entryName.endsWith(".java")) classnames.add(entryName);
		}
		zf.close();

		// is this source file for this bin file?
		boolean valid = isSource(javanames, classnames);

		if (valid) {
			// Calculate MD5 and SHA1 and file size
			InputStream is = Files.newInputStreamSupplier(temp).getInput();
		    MessageDigest md5 = MessageDigest.getInstance("MD5");
		    MessageDigest sha1 = MessageDigest.getInstance("SHA");
		    long readTotal = 0;
		    byte[] buffer = new byte[2048];
		    int read = is.read(buffer, 0, 2048);
		    while (read > -1) {
		    	readTotal += read;
		        md5.update(buffer, 0, read);
		        sha1.update(buffer, 0, read);
		        read = is.read(buffer, 0, 2048);
		    }
		    String md5Str = Hex.encodeHexString(md5.digest());
		    String sha1Str = Hex.encodeHexString(sha1.digest());
		    long size = readTotal;
			Link info2 = new Link();
			info2.setMd5(md5Str);
			info2.setSha1(sha1Str);
			info2.setSize(String.valueOf(size));
			info2.setNames(classnames);
			info2.setSrc(src);
			result.put(path, info2);
			System.out.println("->bin:" + path.substring("http://archive.apache.org/dist/".length()) + "; src:" + src.substring("http://archive.apache.org/dist/".length()));
		}

	}

	private static Map<String, File> cache = new LinkedHashMap<String, File>();
	private static File download(DefaultHttpClient httpclient, String url) throws Exception {
		File temp;
		System.out.println("Downloading url:" + url);
		if (cache.containsKey(url)) {
		    temp = File.createTempFile("tmp", ".zip");
			Files.copy(cache.get(url), temp);
		} else {
			HttpResponse response = httpclient.execute(new HttpGet(url));
			HttpEntity entity = response.getEntity();
			long length = entity.getContentLength();
		    temp = File.createTempFile("tmp", ".zip");
			InputStream is = null;
		    OutputStream fos = null;
		    try {
		    	is = entity.getContent();
		    	fos = Files.newOutputStreamSupplier(temp).getOutput();
		    	IOUtils.copy(is, fos);
		    } finally {
				IOUtils.closeQuietly(fos);
				IOUtils.closeQuietly(is);
		    }
			if (temp.length() != length) {
				temp.delete();
				throw new IllegalStateException();
			}
			if (url.toLowerCase().endsWith(".zip") || url.toLowerCase().endsWith(".jar")) {
				// TODO: verify integrity
			}

		    File tmp = File.createTempFile("tmp", ".zip");
			cache.put(url, tmp);
		}
		if (cache.size() > 10) {
			Map.Entry<String, File> entry = cache.entrySet().iterator().next();
			cache.remove(entry.getKey());
			entry.getValue().delete();
		}
		return temp;
	}

	private static boolean isSource(List<String> javanames, List<String> classnames) {
		Set<String> javanames2 = new HashSet<String>();
		for (String javaname : javanames) {
			String name = FilenameUtils.getName(javaname);
			if (name.endsWith(".java")) {
				javanames2.add(name.substring(0, name.length() - ".java".length()));
			}
		}
		Set<String> classnames2 = new HashSet<String>();
		for (String classname : classnames) {
			String name = FilenameUtils.getName(classname);
			if (name.endsWith(".class") && !name.contains("$")) {
				classnames2.add(name.substring(0, name.length() - ".class".length()));
			}
		}
		Set<String> intersec = Sets.intersection(javanames2, classnames2);
		int commonCount = intersec.size();

		return ((double)commonCount/ classnames2.size()) >= 0.5;
	}

	private static Link getFileInfo(DefaultHttpClient httpclient, String url, Map<String, Link> cache) throws Exception {

		String md5Str;
		String sha1Str;
		long size;

		System.out.println("getFileInfo " + url.substring("http://archive.apache.org/dist/".length()));

		List<String> names = getFileNames(httpclient, url);

		if (cache.containsKey(url + ".md5") && cache.containsKey(url + ".sha1")) {
			// fast way
			System.out.println("FAST");

			HttpResponse response = httpclient.execute(new HttpHead(url));
			size = Long.parseLong(response.getFirstHeader("Content-Length").getValue());

			response = httpclient.execute(new HttpGet(url + ".md5"));
			InputStream is2 = response.getEntity().getContent();
			md5Str = StringUtils.split(StringUtils.trimToEmpty(IOUtils.toString(is2)))[0];
			IOUtils.closeQuietly(is2);

			response = httpclient.execute(new HttpGet(url + ".sha1"));
			InputStream is3 = response.getEntity().getContent();
			sha1Str = StringUtils.split(StringUtils.trimToEmpty(IOUtils.toString(is3)))[0];
			IOUtils.closeQuietly(is3);

		} else {
			// slow way
			System.out.println("FAST");

			HttpResponse response = httpclient.execute(new HttpGet(url));
			size = response.getEntity().getContentLength();

			InputStream is = response.getEntity().getContent();
		    MessageDigest md5 = MessageDigest.getInstance("MD5");
		    MessageDigest sha1 = MessageDigest.getInstance("SHA");
		    long readTotal = 0;
		    byte[] buffer = new byte[2048];
		    int read = is.read(buffer, 0, 2048);
		    while (read > -1) {
		    	readTotal += read;
		        md5.update(buffer, 0, read);
		        sha1.update(buffer, 0, read);
		        read = is.read(buffer, 0, 2048);
		    }
		    md5Str = Hex.encodeHexString(md5.digest());
		    sha1Str = Hex.encodeHexString(sha1.digest());
		    if (readTotal != size) throw new IllegalStateException();
		}


		Link info = new Link();
	    info.setMd5(md5Str);
	    info.setSha1(sha1Str);
	    info.setSize(String.valueOf(size));
	    info.setNames(names);

		return info;
	}

	private static List<String> getFileNames(DefaultHttpClient httpclient,
			String url) throws Exception {
		System.out.println("getFileNames: " + url.substring("http://archive.apache.org/dist/".length()));

		List<String> names = new ArrayList<String>();

		File tmp = download(httpclient, url);
		InputStream is = null;
		ZipInputStream zis = null;
		try {
			is = Files.newInputStreamSupplier(tmp).getInput();
			zis = new ZipInputStream(is);
			ZipEntry entry;
			do {
				entry = zis.getNextEntry();
				if (entry == null) break;
				if (entry.getName().endsWith(".class") || entry.getName().endsWith(".java")) names.add(entry.getName());
			} while (true);
		} finally {
			IOUtils.closeQuietly(zis);
			IOUtils.closeQuietly(is);
			tmp.delete();
		}
		return names;
	}

	private static List<String> sortBinBySize(
			final Map<String, String> bin2Source,
			final Map<String, Double> sizes) {
		List<String> sortedBins = new ArrayList<String>(bin2Source.keySet());
		Collections.sort(sortedBins, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				double diff = (sizes.get(o1) + sizes.get(bin2Source.get(o1)) - sizes.get(o2) - sizes.get(bin2Source.get(o2)));
				return (Math.abs(diff) < 0.1) ? 0 : ((diff > 0) ? 1 : -1);
			}

		});
		return sortedBins;
	}

	private static Map<String, Double> loadURLSize(Map<String, Link> links) {
		Map<String, Double> result = new HashMap<String, Double>();
		for (String  url : links.keySet()) {
			if (!url.contains("!/")) {
				Double size = Double.parseDouble(links.get(url).getSize());
				result.put(url, size);
			}
		}
		return result;
	}

	private static Map<String, String> buildBinUrl2SourceUrlMap(Map<String, List<String>> groups) throws IOException {
		Map<String, Map<String, String>> groupsBin2Source = new HashMap<String, Map<String, String>>();

		Set<String> groupNames = new TreeSet<String>();
		groupNames.addAll(groups.keySet());

		Set<String> bins = new HashSet<String>();

		for (String groupName : groupNames) {
			List<String> group = groups.get(groupName);

			Map<String, String> bin2Source = buildBin2SourceMap(group);
			if (!bin2Source.isEmpty()) {
				groupsBin2Source.put(groupName, bin2Source);
				bins.addAll(bin2Source.keySet());
			}
		}

		Map<String, String> bin2Source = new TreeMap<String, String>();
		for (String groupName : groupsBin2Source.keySet()) {
			Map<String, String> group = groupsBin2Source.get(groupName);
			for (String bin :  group.keySet()) {
				bin2Source.put(groupName + bin, groupName + group.get(bin));
			}
		}
		return bin2Source;
	}

	public static Map<String, String> buildBin2SourceMap(List<String> group) {

		Set<String> sources = new TreeSet<String>();
		Set<String> binaries = new TreeSet<String>();
		Set<String> unknown = new TreeSet<String>();
		Map<String, String> mappedNames = new HashMap<String, String>();
		Pattern[] commonPatterns = new Pattern[]{
				Pattern.compile("[^a-zA-Z]+(windows)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(windows)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("[^a-zA-Z]+(win32)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(win32)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("[^a-zA-Z]+(unix)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(unix)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("[^a-zA-Z]+(distro)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(distro)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("[^a-zA-Z]+(release)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(release)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE)
		};
		Pattern[] srcPatterns = new Pattern[]{
				Pattern.compile("[^a-zA-Z]+(sources)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(sources)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("[^a-zA-Z]+(source)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(source)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("[^a-zA-Z]+(src)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(src)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE)
		};
		Pattern[] binPatterns = new Pattern[]{
				Pattern.compile("[^a-zA-Z]+(binaries)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(binaries)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("[^a-zA-Z]+(binary)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(binary)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("[^a-zA-Z]+(bin)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(bin)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("[^a-zA-Z]+(jars)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE),
				Pattern.compile("^(jars)[^a-zA-Z]+", Pattern.CASE_INSENSITIVE)
		};
		for (String path : group) {
			if (path.endsWith(".zip") || path.endsWith(".jar")) {

				String stripped = FilenameUtils.getName(path);
				boolean found = false;

				for (Pattern commonPattern : commonPatterns) {
					Matcher matcher = commonPattern.matcher(path.toLowerCase());
					if (matcher.find()) {
						stripped = stripped.replaceAll(matcher.group(1), "");
					}
				}

				for (Pattern srcPattern : srcPatterns) {
					Matcher matcher = srcPattern.matcher(path.toLowerCase());
					if (matcher.find()) {
						sources.add(path);
						stripped = stripped.replaceAll(matcher.group(1), "");
						found = true;
					}
				}

				if (!found) {
					for (Pattern binPattern : binPatterns) {
						Matcher matcher = binPattern.matcher(path.toLowerCase());
						if (matcher.find()) {
							binaries.add(path);
							stripped = stripped.replace(matcher.group(1), "");
							found = true;
						}
					}
					if (!found && path.endsWith(".jar")) {
						binaries.add(path);
						found = true;
					}
				}
				if (!found) {
					unknown.add(path);
				}

				stripped = stripped.substring(0, stripped.length() - 3); // zip, jar
				stripped = stripped.replaceAll("[^a-zA-Z0-9]", "");
				stripped = stripped.toLowerCase();
				mappedNames.put(path, stripped);

			}
		}

		// Categorize unknown set into binaries and sources sets
		List<String> newBin = new ArrayList<String>();
		for (String unknownPath : unknown) {
			for (String source : sources) {
				String str1 = mappedNames.get(source);
				String str2 = mappedNames.get(unknownPath);
				if (str1.equals(str2)) {
					newBin.add(unknownPath);
					break;
				}
			}
		}
		binaries.addAll(newBin);
		unknown.removeAll(newBin);
		for (String unknownPath : unknown) {
			sources.add(unknownPath);
			binaries.add(unknownPath);
		}
		unknown.clear();

		// Build map (bin->source)
		Map<String, String> bin2Source = new HashMap<String, String>();
		for (String bin : binaries) {
			String binStandardizedName = mappedNames.get(bin);
			int binLevel = StringUtils.countMatches(bin, "/");
			String src = null;
			for (String source : sources) {
				String srcStandardizedName = mappedNames.get(source);
				int srcLevel = StringUtils.countMatches(source, "/");
				if (binStandardizedName.equals(srcStandardizedName) && (src == null || srcLevel == binLevel)) {
					src = source;
				}
			}
			if (src != null) {
				bin2Source.put(bin, src);
			}
		}
		return bin2Source;

	}

	private static State loadState() throws IOException {


		XStream xstream = new XStream();
		xstream.alias("link", Link.class);
		xstream.alias("state", State.class);
		File file = new File("urls2.xml");
		if (!file.exists()) file = new File("urls.xml");

		State state = (State) xstream.fromXML(file);;

		Map<String, Link> links = state.getLinks();

		Map<String, Link> newlinks = new HashMap<String, Link>();
		final String[] suffixes = new String[]{".zip", ".jar", ".zip.sha1", ".zip.md5", ".jar.sha1", ".jar.md5"};
		final String[] excludes = new String[]{"/activemq/activemq-cpp/", "/santuario/c-library/",
				"/perl/", "/ws/axis-c/","/apr/", "/httpd/", "/ibatis.net/",
				"/logging/log4cxx/", "/logging/log4net/", "/logging/log4php/",
				"/ws/axis2-c/", "/ws/axis2/c/", "/xml/xalan-c/", "/xml/xerces-c/", "/jk2/",
				"/jk/",  "/subversion/", "/spamassassin/", "/jserv/", "/ooo/", "/buildr/",
				"/ws/woden/1.0m9/",
				"/harmony/", "current", "previous", "latest", "-docs", "-javadoc", "-manual"};

		for (Map.Entry<String, Link> entry : links.entrySet()) {

			String url = StringUtils.trimToEmpty(entry.getKey());

			if (url.contains("woden-1.0M9-dom.zip")) {
				System.out.println("woden-1.0M9-dom.zip");
			}

			if (StringUtils.isNotEmpty(url)) {
				String test = url.toLowerCase();
				for (String suffix: suffixes) {
					if (test.endsWith(suffix)) {
						boolean valid = true;
						for (String exclude : excludes) {
							if (test.contains(exclude)) {
								valid = false;
								break;
							}
						}
						if (valid) {
							newlinks.put(entry.getKey(), entry.getValue());
							break;
						}
					}
				}
			}

		}

		state.setLinks(newlinks);

		return state;
	}

	private static Map<String, List<String>> buildGroups(final Map<String, Link> links) {
		Map<String, List<String>> grouping = new HashMap<String, List<String>>();

		Pattern pattern = Pattern.compile("/[^/]*[0-9]+\\.[0-9]+[^/]*/");
		for (String url : links.keySet()) {
			if (!url.contains("!/")) {
				String group;
				group = getGroupName(pattern, url);
				List<String> items = grouping.get(group);
				if (items == null) {
					items = new ArrayList<String>();
					grouping.put(group, items);
				}
				items.add(url.substring(group.length()));
			}
		}
		return grouping;
	}

	public static String getGroupName(Pattern pattern, String url) {
		String group;
		if (pattern.matcher(url).find()) {
			String reverse = StringUtils.reverse(url);
			Matcher matcher = pattern.matcher(reverse);
			matcher.find();
			String matched = matcher.group();
			int remainingNum = reverse.indexOf(matched);
			int startIndex = url.length() - remainingNum - matched.length();
			group = url.substring(0, startIndex + 1);
		} else {
			group = url.substring(0, url.lastIndexOf('/') + 1);
		}
		String[] removed = new String[]{"/binaries/", "/binary/", "/bin/", "/sources/", "/source/", "/src/", "/jars/"};
		for (String remove : removed) {
			if (group.toLowerCase().endsWith(remove)) {
				group = group.substring(0, group.length() - remove.length() + 1);
			}
		}
		return group;
	}

}
