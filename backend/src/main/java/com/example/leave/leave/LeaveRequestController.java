package com.example.leave.leave;

import com.example.leave.auth.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService service;

    public LeaveRequestController(LeaveRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LeaveRequestResponse> submit(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody LeaveRequestCreate body) {
        LeaveRequestResponse created = service.submit(user, body);
        return ResponseEntity.created(URI.create("/api/leave-requests/" + created.id())).body(created);
    }

    @GetMapping("/mine")
    public List<LeaveRequestResponse> mine(@AuthenticationPrincipal CurrentUser user) {
        return service.mine(user);
    }

    @PostMapping("/{id}/cancel")
    public LeaveRequestResponse cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return service.cancel(user, id);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('MANAGER')")
    public List<LeaveRequestResponse> pending(@AuthenticationPrincipal CurrentUser user) {
        return service.pendingForManager(user);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public LeaveRequestResponse approve(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody(required = false) DecisionRequest body) {
        return service.decide(user, id, LeaveStatus.APPROVED, body == null ? null : body.comment());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public LeaveRequestResponse reject(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody RejectRequest body) {
        return service.decide(user, id, LeaveStatus.REJECTED, body.comment());
    }

    @GetMapping("/history")
    public PageResponse<LeaveRequestResponse> history(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(user, status, from, to, page, size);
    }
}
