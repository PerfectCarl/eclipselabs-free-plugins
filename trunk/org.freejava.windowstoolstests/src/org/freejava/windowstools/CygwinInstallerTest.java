package org.freejava.windowstools;

import static org.junit.Assert.*;

import org.junit.Test;

public class CygwinInstallerTest {

	@Test
	public void testInstall() {
		CygwinInstaller obj = new CygwinInstaller();
		obj.install("C:\1");
	}

}
