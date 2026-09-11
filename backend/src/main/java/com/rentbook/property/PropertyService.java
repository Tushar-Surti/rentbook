package com.rentbook.property;

import com.rentbook.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** A landlord's portfolio. Every lookup is scoped to the calling landlord; anything else is "not found". */
@Service
public class PropertyService {

    private final PropertyRepository properties;
    private final UnitRepository units;

    PropertyService(PropertyRepository properties, UnitRepository units) {
        this.properties = properties;
        this.units = units;
    }

    public record PropertyWithUnits(Property property, List<Unit> units) {
    }

    @Transactional(readOnly = true)
    public List<PropertyWithUnits> portfolio(UUID landlordId) {
        List<Property> owned = properties.findByLandlordIdOrderByName(landlordId);
        Map<UUID, List<Unit>> byProperty = units.findByPropertyIdInOrderByLabel(owned.stream().map(Property::getId).toList())
                .stream().collect(Collectors.groupingBy(Unit::getPropertyId));
        return owned.stream()
                .map(property -> new PropertyWithUnits(property, byProperty.getOrDefault(property.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PropertyWithUnits get(UUID landlordId, UUID propertyId) {
        Property property = owned(landlordId, propertyId);
        return new PropertyWithUnits(property, units.findByPropertyIdInOrderByLabel(List.of(property.getId())));
    }

    @Transactional
    public Property create(UUID landlordId, String name, Property.Kind kind, String addressLine, String city,
                           String pincode) {
        return properties.save(new Property(landlordId, name, kind, addressLine, city, pincode));
    }

    @Transactional
    public Property update(UUID landlordId, UUID propertyId, String name, String addressLine, String city,
                           String pincode) {
        Property property = owned(landlordId, propertyId);
        property.update(name, addressLine, city, pincode);
        return property;
    }

    @Transactional
    public Unit addUnit(UUID landlordId, UUID propertyId, UUID parentUnitId, Unit.Kind kind, String label,
                        Long defaultRentPaise, Long defaultDepositPaise) {
        Property property = owned(landlordId, propertyId);
        if (kind == Unit.Kind.BED) {
            Unit room = parentUnitId == null ? null : units.findById(parentUnitId).orElse(null);
            if (room == null || room.getKind() != Unit.Kind.ROOM || !room.getPropertyId().equals(property.getId())) {
                throw ApiException.badRequest("bed_needs_room", "A bed must belong to a room in the same property.");
            }
        } else if (parentUnitId != null) {
            throw ApiException.badRequest("unit_cannot_nest", "Only beds can sit inside another unit.");
        }
        if (units.labelTaken(property.getId(), parentUnitId, label.strip())) {
            throw ApiException.conflict("label_taken", "Another unit here already uses the label \"" + label.strip() + "\".");
        }
        return units.save(new Unit(property.getId(), parentUnitId, kind, label, defaultRentPaise, defaultDepositPaise));
    }

    @Transactional
    public Unit updateUnit(UUID landlordId, UUID unitId, String label, Long defaultRentPaise, Long defaultDepositPaise,
                           Boolean active) {
        Unit unit = ownedUnit(landlordId, unitId);
        if (!unit.getLabel().equalsIgnoreCase(label.strip())
                && units.labelTaken(unit.getPropertyId(), unit.getParentUnitId(), label.strip())) {
            throw ApiException.conflict("label_taken", "Another unit here already uses the label \"" + label.strip() + "\".");
        }
        unit.update(label, defaultRentPaise, defaultDepositPaise);
        if (active != null) {
            if (!active && unit.getStatus() == Unit.Status.OCCUPIED) {
                throw ApiException.conflict("unit_occupied", "End the lease before deactivating this unit.");
            }
            unit.setActive(active);
        }
        return unit;
    }

    /** The unit, if the landlord owns it and it can carry its own lease (a room with beds cannot). */
    @Transactional(readOnly = true)
    public Unit leasableUnit(UUID landlordId, UUID unitId) {
        Unit unit = ownedUnit(landlordId, unitId);
        if (unit.getKind() == Unit.Kind.ROOM && units.existsByParentUnitId(unit.getId())) {
            throw ApiException.badRequest("room_has_beds", "This room is let bed by bed. Invite a tenant to a bed instead.");
        }
        if (unit.getStatus() != Unit.Status.VACANT) {
            throw ApiException.conflict("unit_not_vacant", "This unit is not vacant.");
        }
        return unit;
    }

    public Unit ownedUnit(UUID landlordId, UUID unitId) {
        return units.findOwned(unitId, landlordId).orElseThrow(() -> ApiException.notFound("Unit"));
    }

    private Property owned(UUID landlordId, UUID propertyId) {
        return properties.findByIdAndLandlordId(propertyId, landlordId).orElseThrow(() -> ApiException.notFound("Property"));
    }
}
