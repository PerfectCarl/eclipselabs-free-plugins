package org.freejava.tools.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IPath;
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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

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

    	// Remove invalid selections first
        for (Iterator<IPackageFragmentRoot> it = selections.iterator(); it.hasNext(); ) {
        	IPackageFragmentRoot pkgRoot = it.next();
            try {
                if (pkgRoot.getKind() == IPackageFragmentRoot.K_BINARY
                        //&& pkgRoot.getSourceAttachmentPath() == null
                        && pkgRoot.isArchive()
                        && (pkgRoot.getRawClasspathEntry().getEntryKind() == IClasspathEntry.CPE_LIBRARY
                        || pkgRoot.getRawClasspathEntry().getEntryKind() == IClasspathEntry.CPE_VARIABLE
                        || pkgRoot.getRawClasspathEntry().getEntryKind() == IClasspathEntry.CPE_CONTAINER)) {
                	IPath source = pkgRoot.getSourceAttachmentPath();
                	if (source != null && !source.isEmpty() && new File(source.toOSString()).exists()) {
                		// Valid source found, ignore
                		it.remove();
                	} else {
                		// OK, will process
                	}
                } else {
                	// Not valid selection, ignore
                	it.remove();
                }
            } catch (Exception e) {
				e.printStackTrace();
			}
        }

        // Process valid requests in background
		final Shell shell = HandlerUtil.getActiveWorkbenchWindow(event).getShell();
        if (!selections.isEmpty()) {
	        Job job = new Job("Attaching source to library...") {
	            protected IStatus run(IProgressMonitor monitor) {
	                return updateSourceAttachments(selections, monitor, shell);
	            }
	        };
	        job.setPriority(Job.LONG);
	        job.schedule();
        }

        return null;
    }

    private static IStatus updateSourceAttachments(List<IPackageFragmentRoot> roots, IProgressMonitor monitor, final Shell shell) {

        // Process valid selections
    	Map<String, IPackageFragmentRoot> requests = new HashMap<String, IPackageFragmentRoot>();
        for (IPackageFragmentRoot pkgRoot : roots) {
            File file;
            if (!pkgRoot.isExternal()) {
                file = pkgRoot.getResource().getLocation().toFile();
            } else {
                file = pkgRoot.getPath().toFile();
            }
            try {
            	requests.put(file.getCanonicalPath(), pkgRoot);
            } catch (Exception e) {
				e.printStackTrace();
			}
        }

        final Set<String> notProcessedLibs = new HashSet<String>();
        notProcessedLibs.addAll(requests.keySet());

        List<SourceFileResult> responses = Collections.synchronizedList(new ArrayList<SourceFileResult>());
        List<String> libs = new ArrayList<String>();
        libs.addAll(requests.keySet());
        FinderManager mgr = new FinderManager();
        mgr.findSources(libs, responses);

        while (!monitor.isCanceled() && mgr.isRunning() && !notProcessedLibs.isEmpty()) {
        	processLibSources(requests, notProcessedLibs, responses);
        	try {
        		Thread.sleep(1000);
        	} catch (Exception e) {
				// ignore
        		e.printStackTrace();
			}
        }

        mgr.cancel();

        if (!notProcessedLibs.isEmpty()) {
        	processLibSources(requests, notProcessedLibs, responses);
        }

        // Source not found
        if (!notProcessedLibs.isEmpty()) {
        	Display.getDefault().asyncExec(new Runnable() {
    			@Override
    			public void run() {
    	        	SourceCodeLocationDialog dialog = new SourceCodeLocationDialog(
    	        			shell, notProcessedLibs.toArray(new String[notProcessedLibs.size()]));
    	        	dialog.open();
    			}
    		});
        }


        return Status.OK_STATUS;
    }

	private static void processLibSources(
			Map<String, IPackageFragmentRoot> requests,
			Set<String> notProcessedLibs, List<SourceFileResult> responses) {
		while (!responses.isEmpty()) {
			SourceFileResult response = responses.remove(0);
			String binFile = response.getBinFile();
			if (notProcessedLibs.contains(binFile) && response.getSource() != null) {
				notProcessedLibs.remove(response.getBinFile());
				IPackageFragmentRoot pkgRoot = requests.get(binFile);
				String source = response.getSource();
				String suggestedSourceFileName = response.getSuggestedSourceFileName();
				// attach source to library
				try {
					attachSource(pkgRoot, source, suggestedSourceFileName, null);
				} catch (Exception e) {
					// ignore
					e.printStackTrace();
				}
			}
		}
	}

	private static void attachSource(IPackageFragmentRoot root, String sourcePath, String suggestedSourceFileName, String sourceRoot) throws Exception {

		File sourceAttacherDir = new File(System.getProperty("user.home")
                + File.separatorChar + ".sourceattacher");
        if (!sourceAttacherDir.exists()) sourceAttacherDir.mkdirs();
        File sourceFile = new File(sourceAttacherDir, suggestedSourceFileName);
        if (!sourceFile.exists()) {
        	FileUtils.copyFile(new File(sourcePath), sourceFile);
        }
        sourcePath = sourceFile.getAbsolutePath();

        IJavaProject javaProject = root.getJavaProject();
        IClasspathEntry[] entries = (IClasspathEntry[]) javaProject.getRawClasspath().clone();
        boolean attached = false;
        for (int i = 0; i < entries.length; i++) {
            IClasspathEntry entry = entries[i];
            String entryPath;
            if (entry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
                entryPath = JavaCore.getResolvedVariablePath(entry.getPath()).toOSString();
            } else {
                entryPath = entry.getPath().toOSString();
            }
            String rootPath = root.getPath().toOSString();
            if (entryPath.equals(rootPath)) {
                entries[i] = addSourceAttachment(root, entries[i], sourcePath, sourceAttacherDir, sourceRoot);
                attached = true;
                break;
            }
        }
        if (!attached) {
        	root.attachSource(new Path(sourcePath), null, null);
        } else {
        }
    	javaProject.setRawClasspath(entries, null);
    }

    private static IClasspathEntry addSourceAttachment(
            IPackageFragmentRoot root, IClasspathEntry entry,
            String sourcePath, File sourceAttacherDir, String sourceRoot) throws Exception {
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
            JavaCore.setClasspathVariable("SOURCE_ATTACHER",
                    new Path(sourceAttacherDir.getAbsolutePath()), null);
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
}
