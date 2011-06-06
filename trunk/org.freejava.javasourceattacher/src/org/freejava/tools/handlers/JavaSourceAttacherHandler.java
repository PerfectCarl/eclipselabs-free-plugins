package org.freejava.tools.handlers;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.cyberneko.html.parsers.DOMParser;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.html.HTMLCollection;
import org.w3c.dom.html.HTMLDocument;

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
        updateSourceAttachments(selections);

        return null;
    }

    private void updateSourceAttachments(List<IPackageFragmentRoot> roots) {
        for (IPackageFragmentRoot pkgRoot : roots) {
            try {
                if (pkgRoot.getKind() == IPackageFragmentRoot.K_BINARY && pkgRoot.getSourceAttachmentPath() == null && pkgRoot.isArchive()) {
                    File file;
                    if (!pkgRoot.isExternal()) {
                        file = pkgRoot.getResource().getLocation().toFile();
                    } else {
                        file = pkgRoot.getPath().toFile();
                    }

                    // Try to find source using Maven repos
                    SourceCodeFinder mvnFinder = new MavenRepoSourceCodeFinder();
                    File sourceFile = mvnFinder.find(file);
                    if (sourceFile != null) {
                        attachSource(pkgRoot, sourceFile.getAbsolutePath(), "");
                    } else {
                        SourceCodeFinder googleFinder = new GoogleSourceCodeFinder();
                        sourceFile = googleFinder.find(file);
                        if (sourceFile != null) {
                            attachSource(pkgRoot, sourceFile.getAbsolutePath(), null);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }
    protected static void attachSource(IPackageFragmentRoot root, String sourcePath, String sourceRoot) throws JavaModelException {
        IJavaProject javaProject = root.getJavaProject();
        IClasspathEntry[] entries = (IClasspathEntry[])javaProject.getRawClasspath().clone();
        boolean found = false;
        for (int i = 0; i < entries.length; i++){
                IClasspathEntry entry = entries[i];
                String entryPath = entry.getPath().toOSString();
                String rootPath = root.getPath().toOSString();
                if (entryPath.equals(rootPath)) {
                        entries[i] = JavaCore.newLibraryEntry(
                                root.getPath(),
                                sourcePath == null ? null : new Path(sourcePath),
                                sourceRoot == null ? null : new Path(sourceRoot),
                                false);
                        found = true;
                        break;
                }
        }
        if (!found) {
            // try to use file name only
            for (int i = 0; i < entries.length; i++){
                IClasspathEntry entry = entries[i];
                String entryPath = entry.getPath().toOSString();
                String entryName = entryPath.substring(Math.max(entryPath.lastIndexOf('/'), entryPath.lastIndexOf('\\')) + 1);
                String rootPath = root.getPath().toOSString();
                String rootName = rootPath.substring(Math.max(rootPath.lastIndexOf('/'), rootPath.lastIndexOf('\\')) + 1);
                if (entryName.equals(rootName)) {
                    // Problem: lost classpath variable here!!!
                    entries[i] = JavaCore.newLibraryEntry(
                            root.getPath(),
                            sourcePath == null ? null : new Path(sourcePath),
                            sourceRoot == null ? null : new Path(sourceRoot),
                            false);
                    found = true;
                    break;
                }
            }
        }
        javaProject.setRawClasspath(entries, null);
    }

    public static void main(String[] args) throws Exception {
        File bin = new File("D:/projects/free-plugins/org.freejava.javasourceattacher/lib/commons-lang-2.4.jar");
        File src = new MavenRepoSourceCodeFinder().find(bin);
        System.out.println(src);

        File bin2 = new File("D:/projects/free-plugins/org.freejava.javasourceattacher/lib/commons-logging-1.1.1.jar");
        File src2 = new GoogleSourceCodeFinder().find(bin2);
        System.out.println(src2);

    }

}
