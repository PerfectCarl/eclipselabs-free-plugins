package sample.startup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class Categorize {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		List<String> lines = Files.readLines(new File("D:\\projects\\free-plugins\\javasourceattachercrawler\\urls.txt"), Charset.forName("UTF-8"));

		List<String> urls = loadURLs(lines);

		Map<String, List<String>> groups = buildGroups(urls);

		Map<String, String> bin2Source = buildGroupsBin2Source(groups);

		final Map<String, Double> sizes = loadURLSize(lines);

		List<String> sortedBins = sortBinBySize(bin2Source, sizes);

		Map<String, Map<String, Object>> infos = getInfo(bin2Source, sortedBins, urls);

	    System.out.println(sortedBins);


	}

	private static Map<String, Map<String, Object>> getInfo(Map<String, String> bin2Source, List<String> sortedBins, List<String> urls) throws Exception {
		Map<String, Map<String, Object>> result = new HashMap<String, Map<String, Object>>();
		DefaultHttpClient httpclient = new DefaultHttpClient();
		for (int i = 0; i < sortedBins.size(); i++) {
			String bin = sortedBins.get(i);
			String src = bin2Source.get(bin);

System.out.println("src:"+src+";     bin:"+bin );

			Map<String, Object> info = result.get(src);
	        if (info == null) {
	        	info = getFileInfo(httpclient, src, urls);
	        	result.put(src, info);
	        }

	        boolean java = false;
	        List<String> javanames = (List<String>) info.get("names");
	        for (String name : javanames) {
	        	if (name.endsWith(".java")) {
	        		java = true;
	        		break;
	        	}
	        }

	        if (!java) {
	        	continue;
	        }


        	// continue with bin file
        	if (bin.endsWith(".jar")) {
	        	info = getFileInfo(httpclient, src, urls);
		        List<String> classnames = (List<String>) info.get("names");
		        boolean valid = isSource(javanames, classnames);
		        if (valid) {
		        	info.put("src", src);
		        	result.put(bin, info);
		        }
        	} else {
        		File file = download(httpclient, bin);
        		ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
        		ZipEntry entry;
        		do {
        			entry = zis.getNextEntry();
        			if (entry == null) break;
        			if (entry.getName().endsWith(".jar")) {

        				File temp = File.createTempFile("tmp", ".jar");
        			    OutputStream fos = Files.newOutputStreamSupplier(temp).getOutput();
        			    IOUtils.copy(zis, fos);
        				IOUtils.closeQuietly(fos);

        				List<String> classnames = new ArrayList<String>();
        				InputStream is2 = Files.newInputStreamSupplier(temp).getInput();
        				ZipInputStream zis2 = new ZipInputStream(is2);
        				ZipEntry entry2;
        				do {
        					entry2 = zis2.getNextEntry();
        					if (entry2 == null) break;
        					classnames.add(entry2.getName());
        				} while (true);
        				IOUtils.closeQuietly(zis2);
        				IOUtils.closeQuietly(is2);

        				temp.delete();

        		        boolean valid = isSource(javanames, classnames);
        		        if (valid) {
        		        	info.put("src", src);
        		        	result.put(bin, info);
        		        }
        			}

        		} while (true);
        		file.delete();
        	}


			System.out.println((int)((double)i*100/sortedBins.size()));
		}

		return result;
	}

	private static File download(DefaultHttpClient httpclient, String bin) throws Exception {
		HttpResponse response = httpclient.execute(new HttpGet(bin));
		InputStream is = response.getEntity().getContent();
	    File temp = File.createTempFile("tmp", ".zip");
	    OutputStream fos = Files.newOutputStreamSupplier(temp).getOutput();
	    IOUtils.copy(is, fos);
		IOUtils.closeQuietly(fos);
		IOUtils.closeQuietly(is);
		return null;
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
			if (name.endsWith(".class")) {
				classnames2.add(name.substring(0, name.length() - ".class".length()));
			}
		}
		int commonCount = Sets.intersection(javanames2, classnames2).size();

		return commonCount > 10;
	}

	private static Map<String, Object> getFileInfo(DefaultHttpClient httpclient, String url, List<String> urls) throws Exception {

		String md5Str;
		String sha1Str;
		long size;

		List<String> names = getFileNames(httpclient, url);
		System.out.println(names);


		if (urls.contains(url + ".md5") && urls.contains(url + ".sha1")) {
			// fast way
			System.out.println("FAST:" + url);

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
			System.out.println("SLOW:" + url);

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


		Map<String, Object> info = new HashMap<String, Object>();
	    info.put("md5", md5Str);
	    info.put("sha1", sha1Str);
	    info.put("size", String.valueOf(size));
	    info.put("names", names);

		return info;
	}

	private static List<String> getFileNames(DefaultHttpClient httpclient,
			String url) throws IOException, ClientProtocolException {
		List<String> names = new ArrayList<String>();
		HttpGet request = new HttpGet(url);
		HttpResponse response = httpclient.execute(request);
		HttpEntity entity = response.getEntity();
		InputStream is = entity.getContent();
		ZipInputStream zis = new ZipInputStream(is);
		ZipEntry entry;
		do {
			entry = zis.getNextEntry();
			if (entry == null) break;
			names.add(entry.getName());
			//System.out.println(entry.getName());
		} while (true);
		IOUtils.closeQuietly(zis);
		IOUtils.closeQuietly(is);
		request.abort();
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

	private static Map<String, Double> loadURLSize(List<String> lines) {
		Map<String, Double> result = new HashMap<String, Double>();
		for (String line : lines) {
			if (StringUtils.isNotEmpty(line)) {
				String[] array = StringUtils.split(StringUtils.trimToEmpty(line), " ");
				String url = StringUtils.trimToEmpty(array[0]);
				Double size = Double.parseDouble(StringUtils.trimToEmpty(array[1]));
				result.put(url, size);
			}
		}
		return result;
	}

	private static Map<String, String> buildGroupsBin2Source(Map<String, List<String>> groups) throws IOException {
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

	private static List<String> loadURLs(List<String> lines) throws IOException {

		List<String> result = new ArrayList<String>();

		final String[] suffixes = new String[]{".zip", ".jar", ".zip.sha1", ".zip.md5", ".jar.sha1", ".jar.md5"};
		final String[] excludes = new String[]{"/activemq/activemq-cpp/", "/santuario/c-library/",
				"/perl/", "/ws/axis-c/","/apr/", "/httpd/", "/ibatis.net/",
				"/logging/log4cxx/", "/logging/log4net/", "/logging/log4php/",
				"/ws/axis2-c/", "/ws/axis2/c/", "/xml/xalan-c/", "/xml/xerces-c/", "/jk2/",
				"/jk/",  "/subversion/", "/spamassassin/", "/jserv/", "/ooo/", "/buildr/",
				"/harmony/", "current", "previous", "latest", "-docs", "-javadoc", "-manual"};

		for (String line : lines) {
			line = StringUtils.split(line, " ")[0];
			String url = StringUtils.trimToEmpty(line);
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
							result.add(url);
							break;
						}
					}
				}
			}

		}

		return result;
	}

	private static Map<String, List<String>> buildGroups(final List<String> lines) {
		Map<String, List<String>> grouping = new HashMap<String, List<String>>();

		Pattern pattern = Pattern.compile("/[^/]*[0-9]+\\.[0-9]+[^/]*/");
		for (String line : lines) {
			String group;
			group = getGroupName(pattern, line);
			List<String> items = grouping.get(group);
			if (items == null) {
				items = new ArrayList<String>();
				grouping.put(group, items);
			}
			items.add(line.substring(group.length()));
		}
		return grouping;
	}

	public static String getGroupName(Pattern pattern, String line) {
		String group;
		if (pattern.matcher(line).find()) {
			String reverse = StringUtils.reverse(line);
			Matcher matcher = pattern.matcher(reverse);
			matcher.find();
			String matched = matcher.group();
			int remainingNum = reverse.indexOf(matched);
			int startIndex = line.length() - remainingNum - matched.length();
			group = line.substring(0, startIndex + 1);
		} else {
			group = line.substring(0, line.lastIndexOf('/') + 1);
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
