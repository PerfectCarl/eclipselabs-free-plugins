package org.example.manager.impl;

import java.util.List;

import org.example.dao.BundleDao;
import org.example.manager.BundleManager;
import org.example.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class BundleManagerImpl implements BundleManager {
	private BundleDao bundleDao;

    @Autowired
    public BundleManagerImpl(BundleDao bundleDao) {
        this.bundleDao = bundleDao;
    }

	@Override
	public void add(Bundle bundle) {
		bundleDao.persist(bundle);
	}

	@Override
	public List<Bundle> getAll() {
		List<Bundle> result = bundleDao.findAll();
		return result;
	}

	@Override
	public List<Bundle> findByBinMd5(String binMd5) {
		List<Bundle> result = bundleDao.findByBinMd5(binMd5);
		return result;
	}

}
