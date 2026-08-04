package com.pms.masters.controller;

import com.pms.masters.dto.GeneralUserAuditLogDto;
import com.pms.masters.dto.GeneralUserDto;
import com.pms.masters.dto.ResetPasswordRequest;
import com.pms.masters.service.GeneralUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/general-users")
public class GeneralUserController {

    private final GeneralUserService service;

    public GeneralUserController(GeneralUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<GeneralUserDto> list() {
        return service.findActive();
    }

    @GetMapping("/inactive")
    public List<GeneralUserDto> inactive() {
        return service.findInactive();
    }

    @GetMapping("/audit-logs")
    public List<GeneralUserAuditLogDto> auditLogs() {
        return service.auditLogs();
    }

    @GetMapping("/{id}")
    public GeneralUserDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GeneralUserDto create(@Valid @RequestBody GeneralUserDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public GeneralUserDto update(@PathVariable Long id, @Valid @RequestBody GeneralUserDto dto) {
        return service.update(id, dto);
    }

    @PatchMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest body) {
        service.resetPassword(id, body.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable Long id) {
        service.restore(id);
        return ResponseEntity.noContent().build();
    }
}
