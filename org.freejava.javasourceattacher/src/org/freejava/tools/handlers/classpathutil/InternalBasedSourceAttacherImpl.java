package org.freejava.tools.handlers.classpathutil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.beanutils.MethodUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.swt.widgets.Shell;

// copied and modified from org.eclipse.jdt.internal.ui.preferences.SourceAttachmentPropertyPage
public class InternalBasedSourceAttacherImpl implements SourceAttacher {

	public boolean attachSource(IPackageFragmentRoot fRoot, String newSourcePath)
			throws CoreException {

		IPath fContainerPath;
		IClasspathEntry fEntry;

		try {
			fContainerPath= null;
			fEntry= null;
			if (fRoot == null || fRoot.getKind() != IPackageFragmentRoot.K_BINARY) {
				// error
				Logger.debug("error(!=K_BINARY)", null);
				return false;
			}

			IPath containerPath= null;
			IJavaProject jproject= fRoot.getJavaProject();

			// workaround for 3.5
			boolean ise35 = true;
			IClasspathEntry entry0;
			try {
				entry0 = (IClasspathEntry) MethodUtils.invokeExactStaticMethod(JavaModelUtil.class, "getClasspathEntry", new Object[] {fRoot}, new Class[] {IPackageFragmentRoot.class});
				ise35 = false;
			} catch (NoSuchMethodException e) {
				entry0 = fRoot.getRawClasspathEntry();
			} catch (IllegalAccessException e) {
				entry0 = fRoot.getRawClasspathEntry();
			} catch (InvocationTargetException e) {
				entry0 = fRoot.getRawClasspathEntry();
			}

			if (ise35 && entry0 == null) {
				entry0= JavaCore.newLibraryEntry(fRoot.getPath(), null, null);
			} else if (!ise35 && entry0.getEntryKind() == IClasspathEntry.CPE_CONTAINER || ise35 && entry0 != null) {
				containerPath= entry0.getPath();
				ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(containerPath.segment(0));
				IClasspathContainer container= JavaCore.getClasspathContainer(containerPath, jproject);
				if (initializer == null || container == null) {
					// error
					Logger.debug("error(initializer == null || container == null)", null);
					return false;
				}

				IStatus status= initializer.getSourceAttachmentStatus(containerPath, jproject);
				if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_NOT_SUPPORTED) {
					// error
					Logger.debug("error(ATTRIBUTE_NOT_SUPPORTED)", null);
					return false;
				}
				if (status.getCode() == ClasspathContainerInitializer.ATTRIBUTE_READ_ONLY) {
					// error
					Logger.debug("error(ATTRIBUTE_READ_ONLY)", null);
					return false;
				}
				entry0= JavaModelUtil.findEntryInContainer(container, fRoot.getPath());
			}
			fContainerPath= containerPath;
			fEntry= entry0;


			// getNewEntry()
			IClasspathEntry entry;
			CPListElement elem= CPListElement.createFromExisting(fEntry, null);
			IPath srcAttPath = Path.fromOSString(newSourcePath).makeAbsolute();
			if (fEntry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
	        	File sourceAttacherDir = new File(newSourcePath).getParentFile();
	            JavaCore.setClasspathVariable("SOURCE_ATTACHER",
	                    new Path(sourceAttacherDir.getAbsolutePath()), null);
	            srcAttPath = new Path("SOURCE_ATTACHER/" + new File(newSourcePath).getName());
			}
			elem.setAttribute(CPListElement.SOURCEATTACHMENT, srcAttPath);
			entry = elem.getClasspathEntry();

			if (entry.equals(fEntry)) {
				Logger.debug("NO CHANGE", null);
				return true; // no change
			}

			IClasspathEntry newEntry = entry;
			boolean isReferencedEntry = fEntry.getReferencingEntry() != null;

			String[] changedAttributes= { CPListElement.SOURCEATTACHMENT };
			try {
				MethodUtils.invokeExactStaticMethod(BuildPathSupport.class, "modifyClasspathEntry", new Object[] {null, newEntry, changedAttributes, jproject, fContainerPath, isReferencedEntry, new NullProgressMonitor()},
						new Class[] {Shell.class, IClasspathEntry.class, String[].class, IJavaProject.class, IPath.class, boolean.class, IProgressMonitor.class});
			} catch (NoSuchMethodException e) {
				modifyClasspathEntry(null, newEntry, changedAttributes, jproject, fContainerPath, isReferencedEntry, new NullProgressMonitor());
			} catch (IllegalAccessException e) {
				modifyClasspathEntry(null, newEntry, changedAttributes, jproject, fContainerPath, isReferencedEntry, new NullProgressMonitor());
			} catch (InvocationTargetException e) {
				modifyClasspathEntry(null, newEntry, changedAttributes, jproject, fContainerPath, isReferencedEntry, new NullProgressMonitor());
			}

		} catch (CoreException e) {
			// error
			Logger.debug("error", e);
			return false;
		}

		return true;
	}

	/**
	 * Support older version (3.5)
	 *
	 * @param root
	 * @return
	 * @throws JavaModelException
	 *
	 */
	private void modifyClasspathEntry(Object object, IClasspathEntry newEntry,
			String[] changedAttributes, IJavaProject jproject,
			IPath fContainerPath, boolean isReferencedEntry,
			IProgressMonitor progressMonitor) {
		// old 3.5
		try {
			MethodUtils.invokeExactStaticMethod(BuildPathSupport.class, "modifyClasspathEntry", new Object[] {null, newEntry, changedAttributes, jproject, fContainerPath, new NullProgressMonitor()},
				new Class[] {Shell.class, IClasspathEntry.class, String[].class, IJavaProject.class, IPath.class, IProgressMonitor.class});
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

}
