package com.pms.masters.controller;

import com.pms.masters.dto.IpBillingCategoryDto;
import com.pms.masters.dto.RevenueBucketUpdateRequest;
import com.pms.masters.service.IpBillingCategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/ip-billing-categories")
public class IpBillingCategoryController {

    private final IpBillingCategoryService service;

    public IpBillingCategoryController(IpBillingCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<IpBillingCategoryDto> list() {
        return service.findActive();
    }

    @GetMapping("/inactive")
    public List<IpBillingCategoryDto> listInactive() {
        return service.findInactive();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IpBillingCategoryDto create(@Valid @RequestBody IpBillingCategoryDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public IpBillingCategoryDto update(@PathVariable Long id, @Valid @RequestBody IpBillingCategoryDto dto) {
        return service.update(id, dto);
    }

    @PatchMapping("/{id}/revenue-bucket")
    public IpBillingCategoryDto updateRevenueBucket(@PathVariable Long id, @Valid @RequestBody RevenueBucketUpdateRequest body) {
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
