package org.example;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.example.manager.EmployeeManager;
import org.example.model.Employee;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;


@SuppressWarnings("serial")
public class HelloAppEngineServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(HelloAppEngineServlet.class);

	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setContentType("text/plain");
		LOGGER.info("Saying hello using log4j.");
		ApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(getServletContext());
		EmployeeManager em = (EmployeeManager) context.getBean("employeeManagerImpl");
		Employee employee = new Employee() ;
		employee.setFirstName("FirstName");
		employee.setLastName("LastName");
		em.add(employee);

		for (Employee e : em.getAll()) System.out.println(e.getFirstName());
		resp.getWriter().println("Hello, world");
	}
}