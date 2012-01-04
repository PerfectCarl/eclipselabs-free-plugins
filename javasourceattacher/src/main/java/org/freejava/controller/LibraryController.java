package org.freejava.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletRequest;

import org.apache.commons.lang.StringUtils;
import org.freejava.manager.BundleManager;
import org.freejava.manager.LocationManager;
import org.freejava.model.Bundle;
import org.freejava.model.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/libraries")
public class LibraryController {

	@Autowired
	private BundleManager bundleManager;

	@Autowired
	private LocationManager locationManager;

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public List<LibraryArtifact> index(ServletRequest request) throws Exception {
		Map<String, Object[]> criteriaValues = new Hashtable<String, Object[]>();

		String md5 = request.getParameter("md5");
		if (StringUtils.isNotBlank(md5))
			criteriaValues.put("md5", new Object[] {md5});

		String sha1 = request.getParameter("sha1");
		if (StringUtils.isNotBlank(sha1))
			criteriaValues.put("sha1", new Object[] {sha1});

		String origin = request.getParameter("origin");
		if (StringUtils.isNotBlank(origin))
			criteriaValues.put("origin", new Object[] {origin});

		List<Bundle> bundles = bundleManager.findByConditions(criteriaValues);

		// bundleIds
		List<Long> bundleIds = new ArrayList<Long>();
		List<Long> srcIds = new ArrayList<Long>();
		for (Bundle bundle : bundles) {
			bundleIds.add(bundle.getId());
			if (bundle.getSourceId() != null) {
				srcIds.add(bundle.getSourceId());
			}
		}

		// source bundles
		List<Bundle> srcBundles;
		if (srcIds.isEmpty()) {
			srcBundles = new ArrayList<Bundle>();
		} else {
			Map<String, Object[]> values = new Hashtable<String, Object[]>();
			values.put("id", srcIds.toArray());
			srcBundles = bundleManager.findByConditions(values);
		}

		// locations for all bundles
		List<Location> locations;
		if (bundles.isEmpty() && srcBundles.isEmpty()) {
			locations = new ArrayList<Location>();
		} else {
			Map<String, Object[]> values = new Hashtable<String, Object[]>();
			List<Long> bndIds = new ArrayList<Long>(bundleIds);
			for (Bundle srcBundle : srcBundles) {
				bndIds.add(srcBundle.getId());
			}
			values.put("bundleId", bndIds.toArray());
			locations = locationManager.findByConditions(values);
		}

		// Build result
		List<LibraryArtifact> result = new ArrayList<LibraryArtifact>(bundles.size());
		for (Bundle bundle : bundles) {
			LibraryArtifact lib = new LibraryArtifact();
			lib.setMd5(bundle.getMd5());
			lib.setSha1(bundle.getSha1());
			lib.setOrigin(bundle.getOrigin());

			Set<String> urls = new HashSet<String>();
			for (Location location : locations) {
				if (location.getBundleId().equals(bundle.getId())) {
					urls.add(location.getUrl());
				}
			}
			lib.setUrls(urls);

			for (Bundle srcBundle : srcBundles) {
				if (bundle.getSourceId() != null && srcBundle.getId().equals(bundle.getSourceId())) {
					Artifact srcArtifact = new Artifact();
					srcArtifact.setMd5(srcBundle.getMd5());
					srcArtifact.setSha1(srcBundle.getSha1());
					srcArtifact.setOrigin(srcBundle.getOrigin());

					Set<String> srcurls = new HashSet<String>();
					for (Location location : locations) {
						if (location.getBundleId().equals(bundle.getId())) {
							srcurls.add(location.getUrl());
						}
					}
					srcArtifact.setUrls(srcurls);

					lib.setSource(srcArtifact);
					break;
				}
			}

			result.add(lib);
		}

		return result;
	}

}
