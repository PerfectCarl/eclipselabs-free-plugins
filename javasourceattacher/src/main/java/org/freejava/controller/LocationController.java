package org.freejava.controller;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;

import org.freejava.manager.LocationManager;
import org.freejava.model.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/locations")
public class LocationController {

	@Autowired
	private LocationManager manager;

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public Location create(ServletRequest request) {
		String md5 = request.getParameter("md5");
		String url = request.getParameter("url");
		Location location = new Location();
		location.setMd5(md5);
		location.setUrl(url);
		location = manager.add(location);
		return location;
	}

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public List<Location> index(ServletRequest request) throws Exception {
		Map<String, Object[]> criteriaValues = new Hashtable<String, Object[]>();

	    String id = request.getParameter("id");
		if (id != null)
			criteriaValues.put("id", new Object[] {Long.parseLong(id)});

		String md5 = request.getParameter("md5");
		if (md5 != null)
			criteriaValues.put("md5", new Object[] {md5});

		String url = request.getParameter("url");
		if (url != null)
			criteriaValues.put("url", new Object[] {url});

		return manager.findByConditions(criteriaValues);
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@ResponseBody
	public Location findLocation(@PathVariable("id") long id) throws Exception {
		return manager.findById(id);
	}

}
