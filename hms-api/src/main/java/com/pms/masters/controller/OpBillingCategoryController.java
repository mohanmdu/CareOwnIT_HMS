package com.pms.masters.controller;

import com.pms.masters.dto.OpBillingCategoryDto;
import com.pms.masters.dto.RevenueBucketUpdateRequest;
import com.pms.masters.service.OpBillingCategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/op-billing-categories")
public class OpBillingCategoryController {

    private final OpBillingCategoryService service;

    public OpBillingCategoryController(OpBillingCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<OpBillingCategoryDto> list() {
        return service.findActive();
    }

    @GetMapping("/inactive")
    public List<OpBillingCategoryDto> listInactive() {
        return service.findInactive();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpBillingCategoryDto create(@Valid @RequestBody OpBillingCategoryDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public OpBillingCategoryDto update(@PathVariable Long id, @Valid @RequestBody OpBillingCategoryDto dto) {
        return service.update(id, dto);
    }

    @PatchMapping("/{id}/revenue-bucket")
    public OpBillingCategoryDto updateRevenueBucket(@PathVariable Long id, @Valid @RequestBody RevenueBucketUpdateRequest body) {
        return service.updateRevenueBucket(id, body.revenueBucket());
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
