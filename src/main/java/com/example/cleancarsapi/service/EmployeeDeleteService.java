package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.OrgInviteRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * DELETE half of the employee CRUD. Deleting the seat has teeth — everything the
 * person touched is cleaned in one transaction:
 * <ol>
 *   <li>every job card he was assigned to loses the {@code employee_id} (jobs stay,
 *       assignee gone);</li>
 *   <li>his invite rows are deleted (the "invitation is cleared" rule — the
 *       append-only invite log trades history for this org's roster correctness);</li>
 *   <li>a linked account is unlinked ({@code users.org_id = null} — exactly what
 *       self-leave does), so the seat is genuinely free afterwards.</li>
 * </ol>
 * Never a 409 — deletion always succeeds.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeDeleteService {

    private final EmployeeRepository employees;
    private final ServiceOrderRepository serviceOrders;
    private final OrgInviteRepository invites;
    private final UserRepository users;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Employee employee = employees.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("employee", id));

        serviceOrders.clearEmployeeAssignments(id);
        invites.deleteAll(invites.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .filter(invite -> id.equals(invite.getEmployeeId()))
                .toList());
        if (employee.getUserId() != null) {
            users.findById(employee.getUserId()).ifPresent(User::leaveOrg);
        }
        employees.delete(employee);
        log.info("Employee deleted: org={} id={} linkedUser={}",
                orgId, id, employee.getUserId());
    }
}
