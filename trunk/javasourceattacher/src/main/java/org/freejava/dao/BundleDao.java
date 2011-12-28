package org.freejava.dao;

import java.util.List;

import org.freejava.model.Bundle;

public interface BundleDao extends GenericDao<Bundle, Long> {

	List<Bundle> findByBinMd5(String binMd5);

}
