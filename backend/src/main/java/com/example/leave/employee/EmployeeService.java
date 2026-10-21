package com.example.leave.employee;

import com.example.leave.auth.CurrentUser;
import com.example.leave.common.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository employees;

    public EmployeeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list(CurrentUser user) {
        if (!user.isManager()) {
            return List.of(EmployeeResponse.from(require(user.employeeId())));
        }
        return employees.findAllWithManager().stream().map(EmployeeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(CurrentUser user, Long id) {
        if (!user.isManager() && !user.employeeId().equals(id)) {
            throw ApiException.forbidden("Employees may only view their own record");
        }
        return EmployeeResponse.from(require(id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        String email = normalizeEmail(request.email());
        if (employees.existsByEmailIgnoreCase(email)) {
            throw ApiException.invalidField("email", "Email is already in use");
        }
        Employee manager = resolveManager(request.managerId(), null);
        Employee employee = new Employee(request.name().trim(), email, request.role(), manager);
        if (request.active() != null) {
            employee.setActive(request.active());
        }
        return EmployeeResponse.from(employees.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = require(id);
        String email = normalizeEmail(request.email());
        if (employees.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw ApiException.invalidField("email", "Email is already in use");
        }
        if (request.role() != Role.MANAGER && employee.getRole() == Role.MANAGER
                && employees.existsByManagerIdAndActiveTrue(id)) {
            throw ApiException.invalidField("role", "Reassign direct reports before changing this manager's role");
        }
        employee.setManager(resolveManager(request.managerId(), employee));
        employee.setName(request.name().trim());
        employee.setEmail(email);
        employee.setRole(request.role());
        if (request.active() != null) {
            employee.setActive(request.active());
        }
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public void deactivate(CurrentUser user, Long id) {
        Employee employee = require(id);
        if (user.employeeId().equals(id)) {
            throw ApiException.conflict("You cannot deactivate your own account");
        }
        employee.setActive(false);
    }

    private Employee resolveManager(Long managerId, Employee subject) {
        if (managerId == null) {
            return null;
        }
        Employee manager = employees.findById(managerId)
                .orElseThrow(() -> ApiException.invalidField("managerId", "Manager does not exist"));
        if (manager.getRole() != Role.MANAGER || !manager.isActive()) {
            throw ApiException.invalidField("managerId", "Manager must be an active employee with the MANAGER role");
        }
        if (subject != null) {
            Set<Long> visited = new HashSet<>();
            for (Employee current = manager; current != null && visited.add(current.getId());
                    current = current.getManager()) {
                if (current.getId().equals(subject.getId())) {
                    throw ApiException.invalidField("managerId", "An employee cannot report to themselves");
                }
            }
        }
        return manager;
    }

    private Employee require(Long id) {
        return employees.findById(id).orElseThrow(() -> ApiException.notFound("Employee " + id + " not found"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
