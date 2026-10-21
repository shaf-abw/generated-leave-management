package com.example.leave.leave;

import com.example.leave.employee.Employee;
import java.time.Instant;
import java.time.LocalDate;

public record LeaveRequestResponse(Long id, Long employeeId, String employeeName, LeaveType type,
        LocalDate startDate, LocalDate endDate, long days, String reason, LeaveStatus status, Long decidedById,
        String decidedByName, String decisionComment, Instant createdAt, Instant decidedAt) {

    public static LeaveRequestResponse from(LeaveRequest request) {
        Employee decider = request.getDecidedBy();
        return new LeaveRequestResponse(request.getId(), request.getEmployee().getId(),
                request.getEmployee().getName(), request.getType(), request.getStartDate(), request.getEndDate(),
                LeaveDays.between(request.getStartDate(), request.getEndDate()), request.getReason(),
                request.getStatus(), decider == null ? null : decider.getId(),
                decider == null ? null : decider.getName(), request.getDecisionComment(), request.getCreatedAt(),
                request.getDecidedAt());
    }
}
