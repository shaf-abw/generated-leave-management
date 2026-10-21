package com.example.leave.auth;

import com.example.leave.employee.Employee;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUser loadUserByUsername(String username) {
        AppUser user = users.findByUsernameWithEmployee(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        Employee employee = user.getEmployee();
        return new CurrentUser(employee.getId(), user.getUsername(), employee.getRole(), employee.isActive(),
                user.getPasswordHash());
    }
}
