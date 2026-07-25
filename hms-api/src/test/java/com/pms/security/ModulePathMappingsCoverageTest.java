package com.pms.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pms.masters.entity.ModuleKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Structural drift guard: every @RestController's class-level @RequestMapping
 * base path must be covered by ModulePathMappings (or be an explicitly public
 * prefix), so a new controller can never silently fall through
 * ModuleAuthorizationManager's "unmapped path -> deny + WARN" default without
 * the build catching it.
 *
 * Deliberately a plain unit test rather than an application-startup check.
 * ModuleAuthorizationManager itself was built the same way for the same
 * reason: with 79+ endpoints mapped by hand, a single missed entry should
 * fail CI, not crash a live deployment on its next restart. This test is what
 * makes that safe - it's the thing that actually catches a missed mapping,
 * just at build time instead of at boot time.
 */
class ModulePathMappingsCoverageTest {

    /**
     * AuthController's own two endpoints are already individually
     * special-cased by ModuleAuthorizationManager itself rather than through
     * this table - /login via PUBLIC_PREFIXES, /change-password via its own
     * explicit path-equality check (module-independent, any authenticated
     * user). Its class-level base path ("/api/auth") is neither fully public
     * nor fully module-gated, so it can't be represented as one row here.
     */
    private static final Set<String> METHOD_LEVEL_SPECIAL_CASES = Set.of("AuthController");

    @Test
    void everyControllerBasePathIsMappedOrPublic() throws ClassNotFoundException {
        ModulePathMappings mappings = new ModulePathMappings();

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> unmapped = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("com.pms")) {
            Class<?> controllerClass = Class.forName(candidate.getBeanClassName());
            if (METHOD_LEVEL_SPECIAL_CASES.contains(controllerClass.getSimpleName())) {
                continue;
            }
            RequestMapping mapping = controllerClass.getAnnotation(RequestMapping.class);
            if (mapping == null) {
                // e.g. @RestControllerAdvice with no base path - not a real endpoint surface.
                continue;
            }
            for (String basePath : mapping.value()) {
                boolean isPublic = ModulePathMappings.PUBLIC_PREFIXES.stream().anyMatch(basePath::startsWith);
                ModuleKey resolved = mappings.resolve(basePath);
                if (!isPublic && resolved == null) {
                    unmapped.add(controllerClass.getSimpleName() + " -> " + basePath);
                }
            }
        }

        assertTrue(
                unmapped.isEmpty(),
                () -> "Controllers with no ModulePathMappings entry (add one, or add to PUBLIC_PREFIXES if intentionally public): "
                        + unmapped);
    }
}
