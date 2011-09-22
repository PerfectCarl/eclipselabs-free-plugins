package org.freejava.windowstools

import org.apache.commons.io.FileUtils;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

class CygwinInstaller {
	void install(String path) {

		if (new File(path).exists()) return;

		def ant = new AntBuilder()
		File tmpDir = new File(System.getProperty("java.io.tmpdir"))
		String url = "http://cygwin.com/setup.exe";
		def setupFileName = url.substring(url.lastIndexOf('/') + 1)
		ant.get (src: url, dest: tmpDir, usetimestamp: true, verbose: true)

	}

	public static void createExplorerCommand(String name, String progPath) {
		String nameNoSpaces = name.replaceAll(" ", "");
		String cmd = progPath;
		if (cmd.indexOf(' ') != -1) {
			cmd = "\"" + cmd + "\"";
		}
		cmd += " \"%L\""

		Advapi32Util.registryCreateKey(WinReg.HKEY_CLASSES_ROOT, "Directory\\shell", nameNoSpaces)
		Advapi32Util.registrySetStringValue(WinReg.HKEY_CLASSES_ROOT, "Directory\\shell\\" + nameNoSpaces, "", "Open MSYS Here")
		Advapi32Util.registryCreateKey(WinReg.HKEY_CLASSES_ROOT, "Directory\\shell\\" + nameNoSpaces, "command")
		Advapi32Util.registrySetStringValue(WinReg.HKEY_CLASSES_ROOT, "Directory\\shell\\" + nameNoSpaces + "\\command", "", cmd)

		Advapi32Util.registryCreateKey(WinReg.HKEY_CLASSES_ROOT, "Drive\\shell", nameNoSpaces)
		Advapi32Util.registrySetStringValue(WinReg.HKEY_CLASSES_ROOT, "Drive\\shell\\" + nameNoSpaces, "", "Open MSYS Here")
		Advapi32Util.registryCreateKey(WinReg.HKEY_CLASSES_ROOT, "Drive\\shell\\" + nameNoSpaces, "command")
		Advapi32Util.registrySetStringValue(WinReg.HKEY_CLASSES_ROOT, "Drive\\shell\\" + nameNoSpaces + "\\command", "", cmd)
	}
}
