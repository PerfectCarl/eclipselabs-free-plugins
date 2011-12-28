package org.freejava;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.freejava.manager.BundleManager;
import org.freejava.model.Bundle;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;


@SuppressWarnings("serial")
public class HelloAppEngineServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(HelloAppEngineServlet.class);

	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setContentType("text/plain");
		LOGGER.info("Saying hello using log4j.");
		ApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(getServletContext());
		BundleManager em = (BundleManager) context.getBean("bundleManagerImpl");
		Bundle bundle = new Bundle() ;
		bundle.setBinMd5("1234");
		bundle.setSourceMd5("src123456");
		bundle.setSourceUrl("srcurl");

		em.add(bundle);

		for (Bundle e : em.getAll()) System.out.println(e.getSourceMd5());
		for (Bundle e : em.findByBinMd5("1234")) System.out.println(e.getSourceUrl());
		resp.getWriter().println("Hello, world");
	}
}