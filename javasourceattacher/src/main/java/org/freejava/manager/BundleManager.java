package org.freejava.manager;

import java.util.List;
import java.util.Map;

import org.freejava.model.Bundle;

public interface BundleManager {
	void add(Bundle bundle);
	Bundle findById(long id) throws Exception;
	List<Bundle> findByConditions(Map<String, Object[]> criteriaValues);
}
