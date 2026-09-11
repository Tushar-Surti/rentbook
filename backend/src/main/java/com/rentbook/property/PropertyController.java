package com.rentbook.property;

import com.rentbook.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('LANDLORD')")
class PropertyController {

    private static final String PINCODE = "^[1-9][0-9]{5}$";

    private final PropertyService service;

    PropertyController(PropertyService service) {
        this.service = service;
    }

    record CreatePropertyRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull Property.Kind kind,
            @NotBlank @Size(max = 240) String addressLine,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Pattern(regexp = PINCODE, message = "Enter a 6-digit PIN code") String pincode) {
    }

    record UpdatePropertyRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 240) String addressLine,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Pattern(regexp = PINCODE, message = "Enter a 6-digit PIN code") String pincode) {
    }

    record CreateUnitRequest(
            UUID parentUnitId,
            @NotNull Unit.Kind kind,
            @NotBlank @Size(max = 60) String label,
            @PositiveOrZero Long defaultRentPaise,
            @PositiveOrZero Long defaultDepositPaise) {
    }

    record UpdateUnitRequest(
            @NotBlank @Size(max = 60) String label,
            @PositiveOrZero Long defaultRentPaise,
            @PositiveOrZero Long defaultDepositPaise,
            Boolean active) {
    }

    record UnitResponse(UUID id, UUID parentUnitId, Unit.Kind kind, String label, Long defaultRentPaise,
                        Long defaultDepositPaise, Unit.Status status) {
        static UnitResponse of(Unit unit) {
            return new UnitResponse(unit.getId(), unit.getParentUnitId(), unit.getKind(), unit.getLabel(),
                    unit.getDefaultRentPaise(), unit.getDefaultDepositPaise(), unit.getStatus());
        }
    }

    record PropertyResponse(UUID id, String name, Property.Kind kind, String addressLine, String city,
                            String pincode, List<UnitResponse> units) {
        static PropertyResponse of(Property property, List<Unit> units) {
            return new PropertyResponse(property.getId(), property.getName(), property.getKind(),
                    property.getAddressLine(), property.getCity(), property.getPincode(),
                    units.stream().map(UnitResponse::of).toList());
        }
    }

    @GetMapping("/properties")
    List<PropertyResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.portfolio(CurrentUser.id(jwt)).stream()
                .map(entry -> PropertyResponse.of(entry.property(), entry.units()))
                .toList();
    }

    @PostMapping("/properties")
    @ResponseStatus(HttpStatus.CREATED)
    PropertyResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePropertyRequest body) {
        Property property = service.create(CurrentUser.id(jwt), body.name(), body.kind(), body.addressLine(),
                body.city(), body.pincode());
        return PropertyResponse.of(property, List.of());
    }

    @GetMapping("/properties/{id}")
    PropertyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        PropertyService.PropertyWithUnits entry = service.get(CurrentUser.id(jwt), id);
        return PropertyResponse.of(entry.property(), entry.units());
    }

    @PatchMapping("/properties/{id}")
    PropertyResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                            @Valid @RequestBody UpdatePropertyRequest body) {
        service.update(CurrentUser.id(jwt), id, body.name(), body.addressLine(), body.city(), body.pincode());
        return get(jwt, id);
    }

    @GetMapping("/properties/{id}/units")
    List<UnitResponse> units(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(CurrentUser.id(jwt), id).units().stream().map(UnitResponse::of).toList();
    }

    @PostMapping("/properties/{id}/units")
    @ResponseStatus(HttpStatus.CREATED)
    UnitResponse addUnit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                         @Valid @RequestBody CreateUnitRequest body) {
        return UnitResponse.of(service.addUnit(CurrentUser.id(jwt), id, body.parentUnitId(), body.kind(),
                body.label(), body.defaultRentPaise(), body.defaultDepositPaise()));
    }

    @PatchMapping("/units/{id}")
    UnitResponse updateUnit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                            @Valid @RequestBody UpdateUnitRequest body) {
        return UnitResponse.of(service.updateUnit(CurrentUser.id(jwt), id, body.label(), body.defaultRentPaise(),
                body.defaultDepositPaise(), body.active()));
    }
}
