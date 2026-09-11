package com.rentbook.payment;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** A landlord's Razorpay Route linked account, built up one onboarding step at a time. */
@Entity
@Table(name = "payout_accounts")
@AttributeOverride(name = "id", column = @Column(name = "landlord_id"))
public class PayoutAccount extends BaseEntity {

    @Column(name = "rzp_account_id", length = 40)
    private String rzpAccountId;

    @Column(name = "rzp_stakeholder_id", length = 40)
    private String rzpStakeholderId;

    @Column(name = "rzp_product_id", length = 40)
    private String rzpProductId;

    @Column(name = "activation_status", nullable = false, length = 32)
    private String activationStatus = "NOT_STARTED";

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "beneficiary_name", nullable = false, length = 120)
    private String beneficiaryName;

    @Column(nullable = false, length = 11)
    private String ifsc;

    @Column(name = "bank_last4", nullable = false, length = 4)
    private String bankLast4;

    protected PayoutAccount() {
    }

    PayoutAccount(UUID landlordId) {
        super(landlordId);
    }

    void describe(String legalName, String beneficiaryName, String ifsc, String accountNumber) {
        this.legalName = legalName.strip();
        this.beneficiaryName = beneficiaryName.strip();
        this.ifsc = ifsc.strip().toUpperCase();
        String digits = accountNumber.replaceAll("\\D", "");
        this.bankLast4 = digits.substring(Math.max(0, digits.length() - 4));
    }

    void linkedAccount(String accountId) {
        this.rzpAccountId = accountId;
        this.activationStatus = "CREATED";
    }

    void stakeholder(String stakeholderId) {
        this.rzpStakeholderId = stakeholderId;
    }

    void routeProduct(String productId, String status) {
        this.rzpProductId = productId;
        status(status);
    }

    void status(String status) {
        this.activationStatus = status == null ? "UNKNOWN" : status;
    }

    public boolean isActive() {
        return "activated".equalsIgnoreCase(activationStatus);
    }

    public String getRzpAccountId() {
        return rzpAccountId;
    }

    public String getRzpStakeholderId() {
        return rzpStakeholderId;
    }

    public String getRzpProductId() {
        return rzpProductId;
    }

    public String getActivationStatus() {
        return activationStatus;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getIfsc() {
        return ifsc;
    }

    public String getBankLast4() {
        return bankLast4;
    }
}
