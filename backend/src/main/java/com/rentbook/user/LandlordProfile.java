package com.rentbook.user;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Locale;

/** Landlord-only details. Shares its primary key with the landlord's {@link User}. */
@Entity
@Table(name = "landlord_profiles")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class LandlordProfile extends BaseEntity {

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /** Printed on rent receipts when present; tenants need it for HRA claims above the annual threshold. */
    @Column(length = 10)
    private String pan;

    @Column(name = "platform_fee_bps", nullable = false)
    private int platformFeeBps;

    protected LandlordProfile() {
    }

    public LandlordProfile(User landlord, String displayName, int platformFeeBps) {
        super(landlord.getId());
        this.displayName = displayName.strip();
        this.platformFeeBps = platformFeeBps;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPan() {
        return pan;
    }

    public int getPlatformFeeBps() {
        return platformFeeBps;
    }

    /** Given during payout onboarding; printed on the landlord's rent receipts from then on. */
    public void recordPan(String pan) {
        if (pan != null && !pan.isBlank()) {
            this.pan = pan.strip().toUpperCase(Locale.ROOT);
        }
    }
}
