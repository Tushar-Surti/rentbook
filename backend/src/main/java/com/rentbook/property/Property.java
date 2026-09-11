package com.rentbook.property;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "properties")
public class Property extends BaseEntity {

    public enum Kind { PG, APARTMENT, HOUSE }

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(name = "address_line", nullable = false, length = 240)
    private String addressLine;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 6)
    private String pincode;

    protected Property() {
    }

    Property(UUID landlordId, String name, Kind kind, String addressLine, String city, String pincode) {
        this.landlordId = landlordId;
        this.kind = kind;
        update(name, addressLine, city, pincode);
    }

    void update(String name, String addressLine, String city, String pincode) {
        this.name = name.strip();
        this.addressLine = addressLine.strip();
        this.city = city.strip();
        this.pincode = pincode.strip();
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getPincode() {
        return pincode;
    }
}
