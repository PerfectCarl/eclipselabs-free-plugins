package org.example.dao;

import java.util.List;

import org.example.model.Bundle;

public interface BundleDao extends GenericDao<Bundle, Long> {

	List<Bundle> findByBinMd5(String binMd5);

}
