package org.freejava.tools.handlers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class AbstractSourceCodeFinder implements SourceCodeFinder {

	protected Collection<GAV> findGAVFromFile(String binFile) throws Exception {
        Set<GAV> gavs = new HashSet<GAV>();

		// META-INF/maven/commons-beanutils/commons-beanutils/pom.properties
	    ZipInputStream in = new ZipInputStream(new FileInputStream(binFile));
    	byte[] data = new byte[2048];
	    do {
	    	ZipEntry entry = in.getNextEntry();
	    	if (entry == null) {
	    		break;
	    	}

	    	String zipEntryName = entry.getName();
	        if (zipEntryName.startsWith("META-INF/maven/") && zipEntryName.endsWith("/pom.properties")) {
		    	ByteArrayOutputStream os = new ByteArrayOutputStream();
	        	do {
	        		int read = in.read(data);
	        		if (read < 0) break;
	        		os.write(data, 0, read);
	        	} while (true);
	        	Properties props = new Properties();
	        	props.load(new ByteArrayInputStream(os.toByteArray()));
	        	String version = props.getProperty("version");
	        	String groupId = props.getProperty("groupId");
	        	String artifactId = props.getProperty("artifactId");
	        	if (version != null && groupId != null && artifactId != null) {
	                GAV gav = new GAV();
	                gav.setG(groupId);
	                gav.setA(artifactId);
	                gav.setV(version);
	                gavs.add(gav);
	        	}
	        }
	    } while (true);

		return gavs;
	}
}
