package org.freejava.controller;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;

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
		String binMd5 = request.getParameter("binMd5");
		String sourceMd5 = request.getParameter("sourceMd5");
		Bundle bundle = new Bundle();
		bundle.setBinMd5(binMd5);
		bundle.setSourceMd5(sourceMd5);
		bundle = manager.add(bundle);
		return bundle;
	}

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public List<Bundle> index(ServletRequest request) throws Exception {
		Map<String, Object[]> criteriaValues = new Hashtable<String, Object[]>();

	    String id = request.getParameter("id");
		if (id != null)
			criteriaValues.put("id", new Object[] {Long.parseLong(id)});

		String binMd5 = request.getParameter("binMd5");
		if (binMd5 != null)
			criteriaValues.put("binMd5", new Object[] {binMd5});

		String sourceMd5 = request.getParameter("sourceMd5");
		if (sourceMd5 != null)
			criteriaValues.put("sourceMd5", new Object[] {sourceMd5});

		return manager.findByConditions(criteriaValues);
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@ResponseBody
	public Bundle id(@PathVariable("id") long id) throws Exception {
		return manager.findById(id);
	}

}
