package org.freejava.tools.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogManager;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.slf4j.bridge.SLF4JBridgeHandler;

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
        final List<IPackageFragmentRoot> selections = new ArrayList<IPackageFragmentRoot>();
        for (Iterator<?> iterator = structuredSelection.iterator(); iterator
                .hasNext();) {
            IJavaElement aSelection = (IJavaElement) iterator.next();
            if (aSelection instanceof IPackageFragmentRoot) {
                IPackageFragmentRoot pkgRoot = (IPackageFragmentRoot) aSelection;
                selections.add(pkgRoot);
            } else if (aSelection instanceof IJavaProject) {
                IJavaProject p = (IJavaProject) aSelection;
                try {
                    for (IPackageFragmentRoot pkgRoot : p
                            .getPackageFragmentRoots()) {
                        selections.add(pkgRoot);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        Job job = new Job("Attaching source to library...") {
            protected IStatus run(IProgressMonitor monitor) {
                return updateSourceAttachments(selections, monitor);
            }
        };
        job.setPriority(Job.LONG);
        job.schedule();

        return null;
    }

    private static IStatus updateSourceAttachments(List<IPackageFragmentRoot> roots, IProgressMonitor monitor) {
        for (IPackageFragmentRoot pkgRoot : roots) {
            try {
                if (pkgRoot.getKind() == IPackageFragmentRoot.K_BINARY
                        && pkgRoot.getSourceAttachmentPath() == null
                        && pkgRoot.isArchive()
                        && (pkgRoot.getRawClasspathEntry().getEntryKind() == IClasspathEntry.CPE_LIBRARY || pkgRoot
                                .getRawClasspathEntry().getEntryKind() == IClasspathEntry.CPE_VARIABLE)) {
                    File file;
                    if (!pkgRoot.isExternal()) {
                        file = pkgRoot.getResource().getLocation().toFile();
                    } else {
                        file = pkgRoot.getPath().toFile();
                    }

                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    monitor.subTask("Attaching source to library " + file.getName());

                    // Try to find source using Maven repos
                    SourceCodeFinder mvnFinder = new MavenRepoSourceCodeFinder();
                    File sourceFile = mvnFinder.find(file);
                    if (sourceFile != null) {
                        attachSource(pkgRoot, sourceFile.getAbsolutePath(), "");
                    } else {
                        SourceCodeFinder googleFinder = new GoogleSourceCodeFinder(monitor);
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
        return Status.OK_STATUS;
    }

    private static void attachSource(IPackageFragmentRoot root,
            String sourcePath, String sourceRoot) throws Exception {
        IJavaProject javaProject = root.getJavaProject();
        IClasspathEntry[] entries = (IClasspathEntry[]) javaProject
                .getRawClasspath().clone();
        for (int i = 0; i < entries.length; i++) {
            IClasspathEntry entry = entries[i];
            String entryPath;
            if (entry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
                entryPath = JavaCore.getResolvedVariablePath(entry.getPath())
                        .toOSString();
            } else {
                entryPath = entry.getPath().toOSString();
            }
            String rootPath = root.getPath().toOSString();
            if (entryPath.equals(rootPath)) {
                entries[i] = addSourceAttachment(root, entries[i], sourcePath,
                        sourceRoot);
                break;
            }
        }
        javaProject.setRawClasspath(entries, null);
    }

    private static IClasspathEntry addSourceAttachment(
            IPackageFragmentRoot root, IClasspathEntry entry,
            String sourcePath, String sourceRoot) throws Exception {
        IClasspathEntry result;
        int entryKind = entry.getEntryKind();
        // CPE_PROJECT, CPE_LIBRARY, CPE_SOURCE, CPE_VARIABLE or CPE_CONTAINER
        switch (entryKind) {
        case IClasspathEntry.CPE_LIBRARY:
            result = JavaCore.newLibraryEntry(entry.getPath(),
                    sourcePath == null ? null : new Path(sourcePath),
                    sourceRoot == null ? null : new Path(sourceRoot),
                    entry.getAccessRules(), entry.getExtraAttributes(),
                    entry.isExported());
            break;
        case IClasspathEntry.CPE_VARIABLE:
            File file = new File(System.getProperty("user.home")
                    + File.separatorChar + ".sourceattacher");
            JavaCore.setClasspathVariable("SOURCE_ATTACHER",
                    new Path(file.getAbsolutePath()), null);
            Path varAttPath = new Path("SOURCE_ATTACHER/"
                    + new File(sourcePath).getName());
            result = JavaCore.newVariableEntry(entry.getPath(), varAttPath,
                    sourceRoot == null ? null : new Path(sourceRoot),
                    entry.getAccessRules(), entry.getExtraAttributes(),
                    entry.isExported());
            break;
        default:
            result = entry;
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger rootLogger = LogManager.getLogManager()
                .getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        for (int i = 0; i < handlers.length; i++) {
            rootLogger.removeHandler(handlers[i]);
        }
        SLF4JBridgeHandler.install();

        // File bin = new
        // File("D:/projects/free-plugins/org.freejava.javasourceattacher/lib/commons-lang-2.4.jar");
        // File src = new MavenRepoSourceCodeFinder().find(bin);
        // System.out.println(src);

        File bin2 = new File(
                "D:/projects/free-plugins/org.freejava.javasourceattacher/lib/commons-io-1.4.jar");
        File src2 = new GoogleSourceCodeFinder(null).find(bin2);
        System.out.println(src2);

    }

}
