package com.example.leave.auth;

import com.example.leave.employee.Employee;
import com.example.leave.employee.Role;

public record MeResponse(Long id, String name, String email, Role role) {

    public static MeResponse from(Employee employee) {
        return new MeResponse(employee.getId(), employee.getName(), employee.getEmail(), employee.getRole());
    }
}
