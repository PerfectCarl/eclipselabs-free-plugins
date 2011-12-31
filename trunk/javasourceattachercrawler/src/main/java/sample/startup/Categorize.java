package sample.startup;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;

import com.google.common.io.Files;

public class Categorize {

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		List<String> lines = Files.readLines(new File("D:\\projects\\free-plugins\\javasourceattachercrawler\\urls.txt"), Charset.forName("UTF-8"));

		lines = loadURLs(lines);

		Map<String, List<String>> groups = buildGroups(lines);

		Map<String, Map<String, String>> groupsBin2Source = buildGroupsBin2Source(groups, new File("D:\\projects\\free-plugins\\javasourceattachercrawler\\sources.txt"));

	}

	private static Map<String, Map<String, String>> buildGroupsBin2Source(Map<String, List<String>> groups, File file) throws IOException {
		Map<String, Map<String, String>> groupsBin2Source = new HashMap<String, Map<String, String>>();

		Set<String> groupNames = new TreeSet<String>();
		groupNames.addAll(groups.keySet());

		Set<String> bins = new HashSet<String>();

		for (String groupName : groupNames) {
			List<String> group = groups.get(groupName);

			Map<String, String> bin2Source = buildBin2SourceMap(group);
			if (!bin2Source.isEmpty()) {
				groupsBin2Source.put(groupName, bin2Source);

				String output = "\ngroupName:" + groupName;
				output += "\nin:" + group;
				output += "\nout:" + bin2Source + "\n";
				System.out.println(output);
				if (file != null) Files.append(output, file, Charset.forName("UTF-8"));

				bins.addAll(bin2Source.keySet());
			}
		}

		System.out.println("\nBinFiles:" + bins.size());

		return groupsBin2Source;
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
