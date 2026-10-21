package com.example.leave.dashboard;

import com.example.leave.employee.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardResponse(Role role, long pendingCount, long approvedCount, long rejectedCount,
        long approvedDaysThisYear, Long pendingApprovals, Long onLeaveToday) {
}
