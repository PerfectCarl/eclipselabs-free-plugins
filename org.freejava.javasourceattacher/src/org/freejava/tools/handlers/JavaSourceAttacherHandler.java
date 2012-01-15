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
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.freejava.tools.handlers.classpathutil.Logger;
import org.freejava.tools.handlers.classpathutil.MySourceAttacher;
import org.freejava.tools.handlers.classpathutil.SourceAttacher;

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
					File sourceAttacherDir = new File(System.getProperty("user.home")
			                + File.separatorChar + ".sourceattacher");
			        if (!sourceAttacherDir.exists()) sourceAttacherDir.mkdirs();
			        File sourceFile = new File(sourceAttacherDir, suggestedSourceFileName);
			        if (!sourceFile.exists()) {
			        	FileUtils.copyFile(new File(source), sourceFile);
			        }
					attachSource(pkgRoot, sourceFile.getAbsolutePath());
				} catch (Exception e) {
					// ignore
					Logger.debug("Cannot attach to " + pkgRoot.getResource().getLocation().toOSString(), e);
				}
			}
		}
	}
	private static void attachSource(IPackageFragmentRoot root, String sourcePath) throws Exception {
		SourceAttacher attacher;
		try {
			attacher = (SourceAttacher) Class.forName("org.freejava.tools.handlers.classpathutil.InternalBasedSourceAttacherImpl").newInstance();
			attacher.attachSource(root, sourcePath);
			Logger.debug("Attached (type 1) " + sourcePath, null);
		} catch (Exception e) {
			Logger.debug("Cannot attach to " + sourcePath, e);
			attacher = new MySourceAttacher();
			attacher.attachSource(root, sourcePath);
			Logger.debug("Attached (type 2) " + sourcePath, null);
		}
	}
}
