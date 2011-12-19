package org.example.manager;

import java.util.List;

import org.example.model.Bundle;

public interface BundleManager {
	void add(Bundle bundle);
	List<Bundle> getAll();
	List<Bundle> findByBinMd5(String binMd5);
}
