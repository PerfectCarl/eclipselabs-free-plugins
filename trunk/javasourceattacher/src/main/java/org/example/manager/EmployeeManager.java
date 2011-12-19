package org.example.manager;

import java.util.List;

import org.example.model.Employee;

public interface EmployeeManager {
	void add(Employee employee);
	List<Employee> getAll();
}
