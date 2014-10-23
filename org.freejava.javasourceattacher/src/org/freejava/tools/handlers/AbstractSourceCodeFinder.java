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
import org.apache.commons.lang.StringUtils;
import org.apache.maven.scm.ScmBranch;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmRevision;
import org.apache.maven.scm.ScmTag;
import org.apache.maven.scm.ScmVersion;
import org.apache.maven.scm.command.checkout.CheckOutScmResult;
import org.apache.maven.scm.manager.BasicScmManager;
import org.apache.maven.scm.manager.NoSuchScmProviderException;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.scm.repository.ScmRepositoryException;

import com.google.common.io.Files;

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

        if (gavs.size() > 1) gavs.clear(); // a merged file, the result will not be correct
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


}
