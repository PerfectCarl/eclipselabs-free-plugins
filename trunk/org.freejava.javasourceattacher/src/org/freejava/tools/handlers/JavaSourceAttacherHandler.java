package org.freejava.tools.handlers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Handler class for View Class/Package Dependency action.
 *
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class JavaSourceAttacherHandler extends AbstractHandler {
    /**
     * The constructor.
     */
    public JavaSourceAttacherHandler() {
    }

    /**
     * the command has been executed, so extract extract the needed information
     * from the application context.
     */
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ISelection selection = HandlerUtil.getCurrentSelection(event);

        if (!(selection instanceof IStructuredSelection)) {
            return null;
        }
        // * <li><code>org.eclipse.jdt.core.IPackageFragmentRoot</code></li>
        // * <li><code>org.eclipse.jdt.core.IJavaProject</code></li>
        IStructuredSelection structuredSelection = (IStructuredSelection) selection;
        List<IPackageFragmentRoot> selections = new ArrayList<IPackageFragmentRoot>();
        for (Iterator<?> iterator = structuredSelection.iterator(); iterator.hasNext();) {
            IJavaElement aSelection = (IJavaElement) iterator.next();
            if (aSelection instanceof IPackageFragmentRoot) {
                IPackageFragmentRoot pkgRoot = (IPackageFragmentRoot) aSelection;
                selections.add(pkgRoot);
            } else if (aSelection instanceof IJavaProject) {
                IJavaProject p = (IJavaProject) aSelection;
                try {
                    for (IPackageFragmentRoot pkgRoot : p.getPackageFragmentRoots()) {
                        selections.add(pkgRoot);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        findSource(selections);

        return null;
    }

    private void findSource(List<IPackageFragmentRoot> roots) {
        for (IPackageFragmentRoot pkgRoot : roots) {
            try {
                if (pkgRoot.getKind() == IPackageFragmentRoot.K_BINARY && pkgRoot.getSourceAttachmentPath() == null) {
                    Collection<File> jarFiles = new ArrayList<File>();
                    jarFiles.add(pkgRoot.getPath().toFile());
                    Map<File, Map<String, String>> jarFileInfo = getLibInfo(jarFiles);
                    if (!jarFileInfo.isEmpty()) {
                        Map<String, String> srcinfo = jarFileInfo.values().iterator().next();
                        if (srcinfo.get("scope") == null || !srcinfo.get("scope").equals("system")) {
                            String type = srcinfo.get("type");
                            if (type == null || type.equals("jar")) {
                                String groupId = srcinfo.get("groupId");
                                String artifactId = srcinfo.get("artifactId");
                                String version = srcinfo.get("version");
                                System.out.println(groupId + ";" + artifactId + ";" + version);
                                File f = resolveArtifactSrc(groupId, artifactId, version);
                                System.out.println(f.getAbsolutePath());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }

    private static Map<File, Map<String, String>> getLibInfo(Collection<File> jarFiles) throws IOException {
            Properties properties = new Properties();
        InputStream is = JavaSourceAttacherHandler.class.getResourceAsStream("/jarinfo.properties");
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
                    }
            }
            return result;
    }

    public File resolveArtifactSrc(String groupId, String artifactId, String version) throws Exception {
        //creates clear ivy settings
        IvySettings ivySettings = new IvySettings();
        IBiblioResolver resolver = new IBiblioResolver();
        resolver.setM2compatible(true);
        resolver.setName("central");
        ivySettings.addResolver(resolver);
        ivySettings.setDefaultResolver(resolver.getName());
        Ivy ivy = Ivy.newInstance(ivySettings);

        File ivyfile = File.createTempFile("ivy", ".xml");
        ivyfile.deleteOnExit();

        FileUtils.writeStringToFile(ivyfile,
        "<ivy-module version='2.0' xmlns:m='http://ant.apache.org/ivy/maven'><info organisation='apache' module='m2-with-sources' revision='1.0' /><dependencies><dependency org='" +
         groupId + "' name='" +artifactId+ "' rev='" + version + "' conf='default->sources' /></dependencies></ivy-module>");
        String[] confs = new String[]{"default"};
        ResolveOptions resolveOptions = new ResolveOptions().setConfs(confs);

        //init resolve report
        ResolveReport report = ivy.resolve(ivyfile.toURL(), resolveOptions);

        //so you can get the jar library
        File jarArtifactFile = report.getAllArtifactsReports()[0].getLocalFile();

        return jarArtifactFile;
    }

}
