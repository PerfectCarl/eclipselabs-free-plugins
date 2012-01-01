package org.freejava.controller;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;

import org.apache.commons.lang.StringUtils;
import org.freejava.manager.BundleManager;
import org.freejava.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/bundles")
public class BundleController {

	@Autowired
	private BundleManager manager;

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public Bundle create(ServletRequest request) {
		String md5 = request.getParameter("md5");
		String sha1 = request.getParameter("sha1");
		String fileSize = request.getParameter("fileSize");
		String sourceId = request.getParameter("sourceId");

		Bundle bundle = new Bundle();
		bundle.setMd5(md5);
		bundle.setSha1(sha1);
		if (StringUtils.isNotBlank(fileSize))
			bundle.setFileSize(Long.parseLong(fileSize));
		if (StringUtils.isNotBlank(sourceId))
			bundle.setSourceId(Long.parseLong(sourceId));

		bundle = manager.add(bundle);
		return bundle;
	}

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public List<Bundle> index(ServletRequest request) throws Exception {
		Map<String, Object[]> criteriaValues = new Hashtable<String, Object[]>();

	    String id = request.getParameter("id");
		if (StringUtils.isNotBlank(id))
			criteriaValues.put("id", new Object[] {Long.parseLong(id)});

		String md5 = request.getParameter("md5");
		if (StringUtils.isNotBlank(md5))
			criteriaValues.put("md5", new Object[] {md5});

		String sha1 = request.getParameter("sha1");
		if (StringUtils.isNotBlank(sha1))
			criteriaValues.put("sha1", new Object[] {sha1});

		String fileSize = request.getParameter("fileSize");
		if (StringUtils.isNotBlank(fileSize))
			criteriaValues.put("fileSize", new Object[] {fileSize});

		String sourceId = request.getParameter("sourceId");
		if (StringUtils.isNotBlank(sourceId))
			criteriaValues.put("sourceId", new Object[] {sourceId});

		return manager.findByConditions(criteriaValues);
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@ResponseBody
	public Bundle id(@PathVariable("id") long id) throws Exception {
		return manager.findById(id);
	}

}
