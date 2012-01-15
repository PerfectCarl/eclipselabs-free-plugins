package org.freejava.tools.handlers.classpathutil;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class Logger {
	public static void debug(String message, Throwable throwable) {
		if (message != null) JavaPlugin.getDefault().logErrorMessage(message);
		if (throwable != null) JavaPlugin.getDefault().log(throwable);
	}
}
