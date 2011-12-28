package org.freejava.dao.impl;

import java.util.List;

import org.freejava.dao.BundleDao;
import org.freejava.model.Bundle;
import org.springframework.stereotype.Repository;

@Repository
public class BundleDaoImpl extends GenericDaoImpl<Bundle, Long> implements BundleDao {

	@Override
	public List<Bundle> findByBinMd5(String binMd5) {
        List<Bundle> results = getEntityManager().createQuery("select o from Bundle o where o.binMd5 = :1")
        		.setParameter(1, binMd5).getResultList();
        results.size(); // In JPA apps, we don't need this call!!
        return results;
	}
}
