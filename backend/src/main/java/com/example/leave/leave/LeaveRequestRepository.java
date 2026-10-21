package com.example.leave.leave;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>,
        JpaSpecificationExecutor<LeaveRequest> {

    @EntityGraph(attributePaths = {"employee", "decidedBy"})
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDescIdDesc(Long employeeId);

    @EntityGraph(attributePaths = {"employee", "decidedBy"})
    List<LeaveRequest> findByStatusAndEmployeeManagerIdOrderByCreatedAtAscIdAsc(LeaveStatus status, Long managerId);

    @Query("""
            select count(r) > 0 from LeaveRequest r
            where r.employee.id = :employeeId and r.status in :statuses
              and r.startDate <= :endDate and r.endDate >= :startDate
            """)
    boolean existsOverlapping(Long employeeId, LocalDate startDate, LocalDate endDate,
            Collection<LeaveStatus> statuses);

    long countByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);

    long countByStatusAndEmployeeManagerId(LeaveStatus status, Long managerId);

    @Query("""
            select r from LeaveRequest r
            where r.employee.id = :employeeId and r.status = :status
              and r.startDate <= :rangeEnd and r.endDate >= :rangeStart
            """)
    List<LeaveRequest> findByEmployeeAndStatusOverlapping(Long employeeId, LeaveStatus status, LocalDate rangeStart,
            LocalDate rangeEnd);

    @Query("""
            select count(distinct r.employee.id) from LeaveRequest r
            where r.employee.manager.id = :managerId and r.status = :status
              and r.startDate <= :day and r.endDate >= :day
            """)
    long countEmployeesOnLeave(Long managerId, LeaveStatus status, LocalDate day);
}
