package org.freejava.manager;

import java.util.List;
import java.util.Map;

import org.freejava.model.Location;

public interface LocationManager {
	void add(Location bundle);
	Location findById(long id) throws Exception;
	List<Location> findByConditions(Map<String, Object[]> criteriaValues);
}
