package org.freejava.tools.handlers.classpathutil;

import org.eclipse.jdt.core.IPackageFragmentRoot;

public interface SourceAttacher {
	boolean attachSource(IPackageFragmentRoot fRoot, String newSourcePath) throws Exception;
}