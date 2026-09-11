package com.rentbook.payment;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Razorpay's REST API over HTTP Basic auth with the key id and secret. */
@Component
class RazorpayHttpGateway implements RazorpayGateway {

    private static final URI DEFAULT_BASE = URI.create("https://api.razorpay.com");

    private final RestClient client;

    RazorpayHttpGateway(RazorpayProperties properties) {
        // A slow Razorpay must not hold a tenant's request open indefinitely. HTTP/1.1 because the JDK
        // client otherwise attempts an h2c upgrade against plain-HTTP stand-ins in tests and local runs.
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
        requests.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder()
                .requestFactory(requests)
                .baseUrl((properties.baseUrl() == null ? DEFAULT_BASE : properties.baseUrl()).toString())
                .defaultHeaders(headers -> {
                    if (properties.configured()) {
                        headers.setBasicAuth(properties.keyId(), properties.keySecret());
                    }
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new RazorpayException(response.getStatusCode().value(), body(response.getBody()));
                })
                .build();
    }

    @Override
    public String createLinkedAccount(LinkedAccountRequest request) {
        Map<String, Object> registered = new LinkedHashMap<>();
        registered.put("street1", request.street());
        registered.put("street2", request.city());
        registered.put("city", request.city());
        registered.put("state", request.state());
        registered.put("postal_code", request.postalCode());
        registered.put("country", "IN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", request.email());
        body.put("phone", request.phone().replaceAll("\\D", ""));
        body.put("type", "route");
        body.put("legal_business_name", request.legalName());
        body.put("business_type", "individual");
        body.put("contact_name", request.contactName());
        body.put("profile", Map.of("category", "housing", "subcategory", "space_rental",
                "addresses", Map.of("registered", registered)));
        if (request.pan() != null && !request.pan().isBlank()) {
            body.put("legal_info", Map.of("pan", request.pan()));
        }
        return (String) post("/v2/accounts", body).get("id");
    }

    @Override
    public String createStakeholder(String accountId, String name, String email) {
        return (String) post("/v2/accounts/" + accountId + "/stakeholders", Map.of("name", name, "email", email)).get("id");
    }

    @Override
    public RouteProduct requestRouteProduct(String accountId) {
        Map<String, Object> product = post("/v2/accounts/" + accountId + "/products",
                Map.of("product_name", "route", "tnc_accepted", true));
        return new RouteProduct((String) product.get("id"), (String) product.get("activation_status"));
    }

    @Override
    public RouteProduct updateSettlement(String accountId, String productId, SettlementAccount settlement) {
        Map<String, Object> body = Map.of(
                "settlements", Map.of(
                        "account_number", settlement.accountNumber(),
                        "ifsc_code", settlement.ifsc(),
                        "beneficiary_name", settlement.beneficiaryName()),
                "tnc_accepted", true);
        Map<String, Object> product = client.patch()
                .uri("/v2/accounts/{account}/products/{product}", accountId, productId)
                .body(body)
                .retrieve()
                .body(MAP);
        return new RouteProduct((String) product.get("id"), (String) product.get("activation_status"));
    }

    @Override
    public Order createOrder(long amountPaise, String receipt, Map<String, String> notes, Transfer transfer) {
        Map<String, Object> transferBody = new LinkedHashMap<>();
        transferBody.put("account", transfer.account());
        transferBody.put("amount", transfer.amountPaise());
        transferBody.put("currency", "INR");
        transferBody.put("notes", transfer.notes());
        transferBody.put("linked_account_notes", List.copyOf(transfer.notes().keySet()));
        transferBody.put("on_hold", false);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("notes", notes);
        body.put("transfers", List.of(transferBody));

        Map<String, Object> order = post("/v1/orders", body);
        return new Order((String) order.get("id"), ((Number) order.get("amount")).longValue(), (String) order.get("currency"));
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAP =
            new org.springframework.core.ParameterizedTypeReference<>() {
            };

    private Map<String, Object> post(String path, Object body) {
        return client.post().uri(path).body(body).retrieve().body(MAP);
    }

    private static String body(java.io.InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(unreadable body)";
        }
    }
}
