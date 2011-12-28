package org.freejava.manager;

import java.util.List;

import org.freejava.model.Bundle;

public interface BundleManager {
	void add(Bundle bundle);
	List<Bundle> getAll();
	List<Bundle> findByBinMd5(String binMd5);
}
