package com.pms.masters.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

// permittedModules is the kebab-case ModuleKey.key() vocabulary (e.g.
// "patient-registration"), not the Java enum constant name - see
// ModuleKey's own doc comment for why. Null/omitted on create/update means
// "leave unchanged" is NOT the semantic here - RoleService always treats it
// as "no modules" (empty set) rather than a partial-update no-op, matching
// how the frontend picker always submits its full current selection.
//
// permittedRoutes is opaque frontend route strings (NavItem.route), same
// "always submit the full current selection" semantics as permittedModules.
//
// defaultRoute is null/blank for "use the default dashboard" - unlike the
// two lists above, this one genuinely means "leave unchanged" is irrelevant
// since it's a single nullable value, not a collection to replace wholesale.
public record RoleDto(
        Long id,
        @NotBlank String name,
        Boolean active,
        List<String> permittedModules,
        List<String> permittedRoutes,
        String defaultRoute) {
}
