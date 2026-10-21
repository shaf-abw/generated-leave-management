package com.example.leave.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query("select u from AppUser u join fetch u.employee where lower(u.username) = lower(:username)")
    Optional<AppUser> findByUsernameWithEmployee(String username);
}
