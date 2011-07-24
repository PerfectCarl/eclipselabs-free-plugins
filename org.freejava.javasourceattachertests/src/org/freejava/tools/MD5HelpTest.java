package org.freejava.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import junit.framework.TestCase;

import org.apache.commons.codec.digest.DigestUtils;


public class MD5HelpTest extends TestCase {

	public void testMD5() throws FileNotFoundException, IOException {
		System.out.println(DigestUtils.md5Hex(new FileInputStream(new File("C:/Documents and Settings/Thai Ha/My Documents/Downloads/silvertunnel.org_netlib-0.11-alpha.zip"))));
	}
}
