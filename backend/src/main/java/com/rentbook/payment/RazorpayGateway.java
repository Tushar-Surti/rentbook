package com.rentbook.payment;

import java.util.Map;

/**
 * The slice of Razorpay's API this app uses: Route linked-account onboarding and orders that carry a
 * transfer. An interface so tests can stand in a fake or WireMock without touching the network.
 */
public interface RazorpayGateway {

    record LinkedAccountRequest(String email, String phone, String legalName, String contactName,
                                String street, String city, String state, String postalCode, String pan) {
    }

    record SettlementAccount(String accountNumber, String ifsc, String beneficiaryName) {
    }

    /** {@code activationStatus} as Razorpay reports it, e.g. "requested", "under_review", "activated". */
    record RouteProduct(String id, String activationStatus) {
    }

    record Transfer(String account, long amountPaise, Map<String, String> notes) {
    }

    record Order(String id, long amountPaise, String currency) {
    }

    /** POST /v2/accounts with type "route"; returns the linked account id (acc_…). */
    String createLinkedAccount(LinkedAccountRequest request);

    /** POST /v2/accounts/{id}/stakeholders; returns the stakeholder id. */
    String createStakeholder(String accountId, String name, String email);

    /** POST /v2/accounts/{id}/products for the "route" product, accepting the terms. */
    RouteProduct requestRouteProduct(String accountId);

    /** PATCH /v2/accounts/{id}/products/{productId} with the settlement bank account. */
    RouteProduct updateSettlement(String accountId, String productId, SettlementAccount settlement);

    /** POST /v1/orders with {@code transfers}; Razorpay moves the transfer once the order is paid. */
    Order createOrder(long amountPaise, String receipt, Map<String, String> notes, Transfer transfer);
}
