import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringEscapeUtils;

public class Main {
	public static void main(String[] args) throws IOException {
		String inDir = System.getProperty("user.dir");
		if (args.length > 0) inDir = args[0];
		
		Collection<File> jarFiles = FileUtils.listFiles(new File(inDir), new String[]{"jar"}, true);
		Map<File, Map<String, String>> jarFileInfo = getLibInfo(jarFiles);
		
		String outDir = System.getProperty("user.dir");
		if (args.length > 1) outDir = args[1];

		
		File pomFile = new File(new File(outDir), "pom.xml");
		generatePomFile(pomFile, jarFileInfo);
		
	}

	private static void generatePomFile(File pomFile, Map<File, Map<String, String>> jarFileInfo) throws IOException {
		StringBuffer pom = new StringBuffer();
		pom.append("<project xmlns='http://maven.apache.org/POM/4.0.0' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:schemaLocation='http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd'>\n");
		pom.append("<modelVersion>4.0.0</modelVersion>\n");
		pom.append("<groupId>generated</groupId>\n");
		pom.append("<artifactId>myproject</artifactId>\n");
		pom.append("<packaging>war</packaging>\n");
		pom.append("<version>1.0-SNAPSHOT</version>\n\n");
		pom.append("<dependencies>\n");
		
		for (File jarFile : jarFileInfo.keySet()) {
			Map<String, String> mavenInfo = jarFileInfo.get(jarFile);
			pom.append("  <dependency>\n");
			if (mavenInfo.containsKey("groupId")) pom.append("  <groupId>" + StringEscapeUtils.escapeXml(mavenInfo.get("groupId")) + "</groupId>\n");
			pom.append("  <artifactId>" + StringEscapeUtils.escapeXml(mavenInfo.get("artifactId")) + "</artifactId>\n");
			if (mavenInfo.containsKey("version")) pom.append("  <version>" + StringEscapeUtils.escapeXml(mavenInfo.get("version")) + "</version>\n");
			if (mavenInfo.containsKey("type")) pom.append("  <type>" + StringEscapeUtils.escapeXml(mavenInfo.get("type")) + "</type>\n");
			if (mavenInfo.containsKey("scope")) pom.append("  <scope>" + StringEscapeUtils.escapeXml(mavenInfo.get("scope")) + "</scope>\n");
			if (mavenInfo.containsKey("systemPath")) pom.append("  <systemPath>" + StringEscapeUtils.escapeXml(mavenInfo.get("systemPath")) + "</systemPath>\n");
			pom.append("</dependency>\n");
		}
		
		pom.append("</dependencies>\n");
		
		pom.append("<build>\n");
		pom.append("  <plugins>\n");
		pom.append("    <plugin>\n");
		pom.append("      <groupId>org.apache.maven.plugins</groupId>\n");
		pom.append("      <artifactId>maven-eclipse-plugin</artifactId>\n");
		pom.append("      <version>2.8</version>\n");
		pom.append("      <configuration>\n");
		pom.append("        <downloadSources>true</downloadSources>\n");
		pom.append("        <downloadJavadocs>false</downloadJavadocs>\n");
		pom.append("      </configuration>\n");
		pom.append("    </plugin>\n");
		pom.append("  </plugins>\n");
		pom.append("</build>\n");

		pom.append("<repositories>\n");
		pom.append("  <repository>\n");
		pom.append("    <id>alfresco-public</id>\n");
		pom.append("    <url>http://maven.alfresco.com/nexus/content/groups/public</url>\n");
		pom.append("  </repository>\n");
		pom.append("  <repository>\n");
		pom.append("    <id>alfresco-public-snapshots</id>\n");
		pom.append("    <url>http://maven.alfresco.com/nexus/content/groups/public-snapshots</url>\n");
		pom.append("    <snapshots>\n");
		pom.append("      <enabled>true</enabled>\n");
		pom.append("      <updatePolicy>daily</updatePolicy>\n");
		pom.append("    </snapshots>\n");
		pom.append("  </repository>\n");
		pom.append("</repositories>\n");
			
		pom.append("</project>");
		
		FileUtils.writeStringToFile(pomFile, pom.toString());
		System.out.println("pom.xml written to " + pomFile.getAbsolutePath());
		
	}

	private static Map<File, Map<String, String>> getLibInfo(Collection<File> jarFiles) throws IOException {
		Properties properties = new Properties();
	    InputStream is = Main.class.getResourceAsStream("/jarinfo.properties");
	    properties.load(is);
	    is.close();
		Map<File, Map<String, String>> result = new Hashtable<File, Map<String,String>>();
		for (File jarFile : jarFiles) {			
			String name = jarFile.getName();
			String basename = FilenameUtils.getBaseName(name);
			long size = jarFile.length();
			Map<String, String> info = new Hashtable<String, String>();
			result.put(jarFile, info);
			String key = name + ":" + size;
			if (properties.containsKey(key)) {
				String values = properties.getProperty(key);
				String[] mavenInfo = values.split(":"); // org.slf4j, slf4j-api, jar, 1.6.0
				info.put("groupId", mavenInfo[0]);
				info.put("artifactId", mavenInfo[1]);
				info.put("type", mavenInfo[2]);
				info.put("version", mavenInfo[3]);
				if (mavenInfo.length > 4 && "system".equals(mavenInfo[4])) {
					info.put("scope", "system");
					info.put("systemPath", jarFile.getAbsolutePath());					
				}
			} else {
				System.out.println("WARNING: not found maven info for " + jarFile.getAbsolutePath() + ";key=" + key);
				info.put("groupId", basename);
				info.put("artifactId", basename);
				info.put("version", "0.1");
				info.put("scope", "system");
				info.put("systemPath", jarFile.getAbsolutePath());									
			}
			
		}		
		return result;
	}
}