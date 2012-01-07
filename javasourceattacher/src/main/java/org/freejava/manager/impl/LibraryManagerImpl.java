package org.freejava.manager.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.freejava.manager.Artifact;
import org.freejava.manager.BundleManager;
import org.freejava.manager.LibraryArtifact;
import org.freejava.manager.LibraryManager;
import org.freejava.manager.LocationManager;
import org.freejava.model.Bundle;
import org.freejava.model.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LibraryManagerImpl implements LibraryManager {

	@Autowired
	private BundleManager bundleManager;

	@Autowired
	private LocationManager locationManager;

	@Override
	public Bundle create(
			String origin, String md5, String sha1, String[] urls,
			String src_origin, String src_md5, String src_sha1, String[] src_urls) {


		Bundle src_bundle = new Bundle();
		src_bundle.setOrigin(src_origin);
		src_bundle.setMd5(src_md5);
		src_bundle.setSha1(src_sha1);
		src_bundle = bundleManager.add(src_bundle);

		for (String  url : urls) {
			Location location = new Location();
			location.setBundleId(src_bundle.getId());
			location.setUrl(url);
			location = locationManager.add(location);
		}

		Bundle bundle = new Bundle();
		bundle.setOrigin(origin);
		bundle.setMd5(md5);
		bundle.setSha1(sha1);
		bundle.setSourceId(src_bundle.getId());
		bundle = bundleManager.add(bundle);

		for (String  url : urls) {
			Location location = new Location();
			location.setBundleId(bundle.getId());
			location.setUrl(url);
			location = locationManager.add(location);
		}

		return bundle;
	}

	@Override
	public List<LibraryArtifact> findLibrary(String md5, String sha1,
			String origin) {

		List<LibraryArtifact> result;
		Map<String, Object[]> criteriaValues = new Hashtable<String, Object[]>();
		if (StringUtils.isNotBlank(md5))
			criteriaValues.put("md5", new Object[] {md5});
		if (StringUtils.isNotBlank(sha1))
			criteriaValues.put("sha1", new Object[] {sha1});
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
		result = new ArrayList<LibraryArtifact>(bundles.size());
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
