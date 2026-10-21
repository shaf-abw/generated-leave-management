package com.example.leave.leave;

import com.example.leave.auth.CurrentUser;
import com.example.leave.common.ApiException;
import com.example.leave.employee.Employee;
import com.example.leave.employee.EmployeeRepository;
import jakarta.persistence.criteria.JoinType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LeaveRequestService {

    static final int MAX_PAGE_SIZE = 100;
    private static final List<LeaveStatus> BLOCKING_STATUSES = List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    private final LeaveRequestRepository requests;
    private final EmployeeRepository employees;
    private final Clock clock;

    public LeaveRequestService(LeaveRequestRepository requests, EmployeeRepository employees, Clock clock) {
        this.requests = requests;
        this.employees = employees;
        this.clock = clock;
    }

    @Transactional
    public LeaveRequestResponse submit(CurrentUser user, LeaveRequestCreate body) {
        if (body.endDate().isBefore(body.startDate())) {
            throw ApiException.invalidField("endDate", "End date must not be before start date");
        }
        if (body.startDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.invalidField("startDate", "Start date must not be in the past");
        }
        Employee employee = employees.findByIdForUpdate(user.employeeId())
                .orElseThrow(() -> ApiException.notFound("Employee not found"));
        if (requests.existsOverlapping(employee.getId(), body.startDate(), body.endDate(), BLOCKING_STATUSES)) {
            throw ApiException.conflict("Request overlaps an existing pending or approved request");
        }
        String reason = StringUtils.hasText(body.reason()) ? body.reason().trim() : null;
        LeaveRequest saved = requests.save(new LeaveRequest(employee, body.type(), body.startDate(), body.endDate(),
                reason, Instant.now(clock)));
        return LeaveRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> mine(CurrentUser user) {
        return requests.findByEmployeeIdOrderByCreatedAtDescIdDesc(user.employeeId()).stream()
                .map(LeaveRequestResponse::from).toList();
    }

    @Transactional
    public LeaveRequestResponse cancel(CurrentUser user, Long id) {
        LeaveRequest request = require(id);
        if (!request.getEmployee().getId().equals(user.employeeId())) {
            throw ApiException.forbidden("You can only cancel your own requests");
        }
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw ApiException.conflict("Only pending requests can be cancelled");
        }
        request.cancel();
        return LeaveRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> pendingForManager(CurrentUser manager) {
        return requests.findByStatusAndEmployeeManagerIdOrderByCreatedAtAscIdAsc(LeaveStatus.PENDING,
                manager.employeeId()).stream().map(LeaveRequestResponse::from).toList();
    }

    @Transactional
    public LeaveRequestResponse decide(CurrentUser manager, Long id, LeaveStatus decision, String comment) {
        LeaveRequest request = require(id);
        Employee requester = request.getEmployee();
        if (requester.getId().equals(manager.employeeId())) {
            throw ApiException.forbidden("You cannot decide your own request");
        }
        if (requester.getManager() == null || !requester.getManager().getId().equals(manager.employeeId())) {
            throw ApiException.forbidden("Only the requester's manager can decide this request");
        }
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw ApiException.conflict("Only pending requests can be decided");
        }
        Employee decider = employees.getReferenceById(manager.employeeId());
        request.decide(decision, decider, StringUtils.hasText(comment) ? comment.trim() : null, Instant.now(clock));
        return LeaveRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> history(CurrentUser user, LeaveStatus status, LocalDate from,
            LocalDate to, int page, int size) {
        if (page < 0) {
            throw ApiException.invalidField("page", "Page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.invalidField("size", "Size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.invalidField("to", "'to' must not be before 'from'");
        }
        Specification<LeaveRequest> spec = visibleTo(user);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("endDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), to));
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(requests.findAll(spec, pageable), LeaveRequestResponse::from);
    }

    private static Specification<LeaveRequest> visibleTo(CurrentUser user) {
        Long me = user.employeeId();
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("employee", JoinType.INNER);
                root.fetch("decidedBy", JoinType.LEFT);
            }
            var employee = root.get("employee");
            var own = cb.equal(employee.get("id"), me);
            if (!user.isManager()) {
                return own;
            }
            var manager = root.join("employee", JoinType.INNER).join("manager", JoinType.LEFT);
            return cb.or(own, cb.equal(manager.get("id"), me));
        };
    }

    private LeaveRequest require(Long id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Leave request " + id + " not found"));
    }
}
