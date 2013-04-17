package org.freejava.mirthtools;

import java.io.File;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IStartup;

public class ResourceChangeListener implements IResourceChangeListener, IStartup {

    public static void register() {
        IResourceChangeListener listener = new ResourceChangeListener();
        ResourcesPlugin.getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE);
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        try {
            event.getDelta().accept(new IResourceDeltaVisitor() {
                public boolean visit(IResourceDelta delta) throws CoreException {
                    switch (delta.getKind()) {
                    case IResourceDelta.ADDED:
                    case IResourceDelta.CHANGED:
                        final IResource resource = delta.getResource();
                        if (resource instanceof IFile && !resource.isDerived()) {
                            IFile ifile = (IFile) resource;
                            File file = ifile.getLocation().toFile();
                            try {
                                List<File> files = new MirthSupport().onFileChanged(file);
                                if (!files.isEmpty()) {
                                    Job job = new Job("Refreshing MirthConnect changed files...") {
                                        @Override
                                        protected IStatus run(IProgressMonitor monitor) {
                                            try {
                                                resource.getParent().refreshLocal(IResource.DEPTH_INFINITE, null);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            return Status.OK_STATUS;
                                        }
                                        };
                                     job.setPriority(Job.SHORT);
                                     job.schedule(); // start as soon as possible

                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        break;
                    }
                    return true;
                }
            });
        } catch (CoreException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void earlyStartup() {
        register();
    }

}
