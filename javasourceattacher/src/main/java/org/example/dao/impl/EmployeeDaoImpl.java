package org.example.dao.impl;

import org.example.dao.EmployeeDao;
import org.example.model.Employee;
import org.springframework.stereotype.Repository;

@Repository
public class EmployeeDaoImpl extends GenericDaoImpl<Employee, Long> implements EmployeeDao {

}
