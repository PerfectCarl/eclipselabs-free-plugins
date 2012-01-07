package org.freejava.tools.handlers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

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


    @SuppressWarnings("rawtypes")
	protected static boolean isSourceCodeFor(String src, String bin) {
        boolean result = false;
        try {
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
        } catch (Exception e) {
			e.printStackTrace();
		}

        return result;
    }

    protected static String download(String url) throws Exception {
        File file = File.createTempFile("ssourceattacher", ".tmp");

        InputStream is = null;
        OutputStream os = null;
        try {
            URLConnection conn = new URL(url).openConnection();
            is = openConnectionCheckRedirects(conn);
        	os = FileUtils.openOutputStream(file);
            IOUtils.copy(is, os);
        } catch (Exception e) {
        } finally {
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
        }

        return file.getAbsolutePath();
    }

    private static InputStream openConnectionCheckRedirects(URLConnection c) throws IOException
    {
       boolean redir;
       int redirects = 0;
       InputStream in = null;
       do
       {
          if (c instanceof HttpURLConnection)
          {
             ((HttpURLConnection) c).setInstanceFollowRedirects(false);
          }
          c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/535.7 (KHTML, like Gecko) Chrome/16.0.912.63 Safari/535.7");

          // We want to open the input stream before getting headers
          // because getHeaderField() et al swallow IOExceptions.
          in = c.getInputStream();
          redir = false;
          if (c instanceof HttpURLConnection)
          {
             HttpURLConnection http = (HttpURLConnection) c;
             int stat = http.getResponseCode();
             if (stat >= 300 && stat <= 307 && stat != 306 &&
                stat != HttpURLConnection.HTTP_NOT_MODIFIED)
             {
                URL base = http.getURL();
                String loc = http.getHeaderField("Location");
                URL target = null;
                if (loc != null)
                {
                   target = new URL(base, loc);
                }
                http.disconnect();
                // Redirection should be allowed only for HTTP and HTTPS
                // and should be limited to 5 redirections at most.
                if (target == null || !(target.getProtocol().equals("http")
                   || target.getProtocol().equals("https"))
                   || redirects >= 5)
                {
                   throw new SecurityException("illegal URL redirect");
                }
                redir = true;
                c = target.openConnection();
                redirects++;
             }
          }
       } while (redir);
       return in;
    }
}
