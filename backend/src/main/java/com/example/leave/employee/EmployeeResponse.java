package com.example.leave.employee;

public record EmployeeResponse(Long id, String name, String email, Role role, Long managerId, String managerName,
        boolean active) {

    public static EmployeeResponse from(Employee employee) {
        Employee manager = employee.getManager();
        return new EmployeeResponse(employee.getId(), employee.getName(), employee.getEmail(), employee.getRole(),
                manager == null ? null : manager.getId(), manager == null ? null : manager.getName(),
                employee.isActive());
    }
}
