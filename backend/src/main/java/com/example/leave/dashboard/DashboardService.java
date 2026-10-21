package com.example.leave.dashboard;

import com.example.leave.auth.CurrentUser;
import com.example.leave.leave.LeaveDays;
import com.example.leave.leave.LeaveRequestRepository;
import com.example.leave.leave.LeaveStatus;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final LeaveRequestRepository requests;
    private final Clock clock;

    public DashboardService(LeaveRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary(CurrentUser user) {
        Long me = user.employeeId();
        LocalDate today = LocalDate.now(clock);
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);
        long approvedDays = requests.findByEmployeeAndStatusOverlapping(me, LeaveStatus.APPROVED, yearStart, yearEnd)
                .stream()
                .mapToLong(r -> LeaveDays.withinRange(r.getStartDate(), r.getEndDate(), yearStart, yearEnd))
                .sum();
        Long pendingApprovals = null;
        Long onLeaveToday = null;
        if (user.isManager()) {
            pendingApprovals = requests.countByStatusAndEmployeeManagerId(LeaveStatus.PENDING, me);
            onLeaveToday = requests.countEmployeesOnLeave(me, LeaveStatus.APPROVED, today);
        }
        return new DashboardResponse(user.role(),
                requests.countByEmployeeIdAndStatus(me, LeaveStatus.PENDING),
                requests.countByEmployeeIdAndStatus(me, LeaveStatus.APPROVED),
                requests.countByEmployeeIdAndStatus(me, LeaveStatus.REJECTED),
                approvedDays, pendingApprovals, onLeaveToday);
    }
}
