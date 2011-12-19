package org.example.manager.impl;

import java.util.List;

import org.example.dao.EmployeeDao;
import org.example.manager.EmployeeManager;
import org.example.model.Employee;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class EmployeeManagerImpl implements EmployeeManager {
	private EmployeeDao employeeDao;

    @Autowired
    public EmployeeManagerImpl(EmployeeDao employeeDao) {
        this.employeeDao = employeeDao;
    }

	@Override
	public void add(Employee employee) {
		employeeDao.persist(employee);
	}

	@Override
	public List<Employee> getAll() {
		List<Employee> result = employeeDao.findAll();
		return result;
	}

}
