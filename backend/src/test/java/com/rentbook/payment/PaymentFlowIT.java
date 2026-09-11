package com.rentbook.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.jayway.jsonpath.JsonPath;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import com.rentbook.ledger.LedgerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Razorpay Route end to end against a stand-in Razorpay: onboarding, the split order, the browser
 * callback that only waits, and the signed webhook that alone marks rent paid and issues the receipt.
 */
class PaymentFlowIT extends IntegrationTest {

    private static final String KEY_ID = "rzp_test_rentbook";
    private static final String KEY_SECRET = "test-key-secret";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private static final String ONBOARDING = """
            {"legalName":"Lata Iyer","pan":"ABCPI1234K","street":"14 Koramangala 5th Block","city":"Bengaluru",
             "state":"Karnataka","postalCode":"560095","accountNumber":"50100123456789","ifsc":"HDFC0001234",
             "beneficiaryName":"Lata Iyer"}""";

    private static final WireMockServer RAZORPAY = new WireMockServer(options().dynamicPort());

    static {
        RAZORPAY.start();
    }

    @DynamicPropertySource
    static void razorpay(DynamicPropertyRegistry registry) {
        registry.add("rentbook.razorpay.base-url", RAZORPAY::baseUrl);
        registry.add("rentbook.razorpay.key-id", () -> KEY_ID);
        registry.add("rentbook.razorpay.key-secret", () -> KEY_SECRET);
        registry.add("rentbook.razorpay.webhook-secret", () -> WEBHOOK_SECRET);
    }

    @AfterAll
    static void stopRazorpay() {
        RAZORPAY.stop();
    }

    @Autowired
    PayoutService payouts;

    @Autowired
    LedgerService ledger;

    @Autowired
    JdbcTemplate jdbc;

    private record Tenancy(Flows.Session landlord, Flows.Session tenant, String leaseId, String account) {
        String ledgerPath() {
            return "/api/v1/leases/" + leaseId + "/ledger";
        }

        String receiptsPath() {
            return "/api/v1/leases/" + leaseId + "/receipts";
        }
    }

    private record Started(String paymentId, String orderId, long amountPaise) {
    }

    @BeforeEach
    void razorpayTakesOrders() {
        RAZORPAY.resetAll();
        RAZORPAY.stubFor(post("/v1/orders").willReturn(okJson("""
                {"id":"order_{{randomValue length=14 type='ALPHANUMERIC'}}","entity":"order",
                 "amount":{{jsonPath request.body '$.amount'}},"currency":"INR","status":"created"}
                """).withTransformers("response-template")));
    }

    @Test
    void aLandlordBecomesALinkedAccountAndOnlyTheLastFourDigitsAreKept() throws Exception {
        String account = "acc_" + suffix();
        stubOnboarding(account);
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));

        flows.get(landlord, "/api/v1/payouts/account").andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentsConfigured").value(true))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
        flows.authed(mvcPost("/api/v1/payouts/account"), landlord, ONBOARDING)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.bankLast4").value("6789"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist());

        RAZORPAY.verify(postRequestedFor(urlEqualTo("/v2/accounts"))
                .withBasicAuth(new BasicCredentials(KEY_ID, KEY_SECRET))
                .withRequestBody(matchingJsonPath("$.type", equalTo("route")))
                .withRequestBody(matchingJsonPath("$.profile.subcategory", equalTo("space_rental")))
                .withRequestBody(matchingJsonPath("$.legal_info.pan", equalTo("ABCPI1234K"))));
        RAZORPAY.verify(patchRequestedFor(urlEqualTo("/v2/accounts/" + account + "/products/acc_prd_1"))
                .withRequestBody(matchingJsonPath("$.settlements.account_number", equalTo("50100123456789")))
                .withRequestBody(matchingJsonPath("$.settlements.ifsc_code", equalTo("HDFC0001234"))));
        assertThat(jdbc.queryForList("select * from payout_accounts where landlord_id = ?",
                UUID.fromString(landlord.userId())).getFirst().values())
                .noneMatch(value -> String.valueOf(value).contains("50100123456789"));
        // The PAN goes on the landlord's receipts from now on.
        assertThat(jdbc.queryForObject("select pan from landlord_profiles where user_id = ?", String.class,
                UUID.fromString(landlord.userId()))).isEqualTo("ABCPI1234K");
    }

    @Test
    void aRetryAfterRazorpayRefusesResumesWithoutASecondAccount() throws Exception {
        String account = "acc_" + suffix();
        stubOnboarding(account);
        RAZORPAY.stubFor(post("/v2/accounts/" + account + "/products").willReturn(badRequest()));
        String email = Flows.email("lata");
        Flows.Session landlord = flows.registerLandlord(email);

        flows.authed(mvcPost("/api/v1/payouts/account"), landlord, ONBOARDING)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("razorpay_rejected"));
        stubOnboarding(account);
        flows.authed(mvcPost("/api/v1/payouts/account"), landlord, ONBOARDING)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        RAZORPAY.verify(1, postRequestedFor(urlEqualTo("/v2/accounts"))
                .withRequestBody(matchingJsonPath("$.email", equalTo(email))));
    }

    @Test
    void theOrderSendsTheLandlordTheirShareAndTheBrowserCallbackOnlyWaits() throws Exception {
        Tenancy home = moveIn(true);
        Started payment = checkout(home);

        // Deposit ₹25,000 and September's ₹12,500; the landlord bears the 2% fee.
        assertThat(payment.amountPaise()).isEqualTo(3_750_000);
        RAZORPAY.verify(postRequestedFor(urlEqualTo("/v1/orders"))
                .withBasicAuth(new BasicCredentials(KEY_ID, KEY_SECRET))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("3750000")))
                .withRequestBody(matchingJsonPath("$.transfers[0].account", equalTo(home.account())))
                .withRequestBody(matchingJsonPath("$.transfers[0].amount", equalTo("3675000")))
                .withRequestBody(matchingJsonPath("$.transfers[0].on_hold", equalTo("false"))));

        callback(home, payment, "pay_" + suffix(), "0".repeat(64))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_signature"));
        String rzpPayment = "pay_" + suffix();
        String signature = Signatures.hmacHex((payment.orderId() + "|" + rzpPayment).getBytes(StandardCharsets.UTF_8),
                KEY_SECRET);
        callback(home, payment, rzpPayment, signature)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_WEBHOOK"));

        flows.get(home.tenant(), home.ledgerPath()).andExpect(jsonPath("$.outstandingPaise").value(3_750_000));
        flows.get(home.landlord(), home.receiptsPath()).andExpect(jsonPath("$.length()").value(0));
        // Until Razorpay confirms, the slip waits and a second payment for the same charges is refused.
        flows.get(home.tenant(), "/api/v1/dashboard/tenant")
                .andExpect(jsonPath("$.payOnline").value(true))
                .andExpect(jsonPath("$.pendingPayment.id").value(payment.paymentId()))
                .andExpect(jsonPath("$.lastReceipt").isEmpty());
        flows.authed(mvcPost("/api/v1/payments/checkout"), home.tenant(), checkoutBody(home))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_pending"));
    }

    @Test
    void razorpayIsNotAskedForLessThanARupee() throws Exception {
        Tenancy home = moveIn(true);
        String charge = flows.authed(mvcPost("/api/v1/leases/" + home.leaseId() + "/charges"), home.landlord(), """
                        {"kind":"OTHER","description":"Rounding","amountPaise":50,"dueOn":"%s"}""".formatted(ledger.today()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        flows.authed(mvcPost("/api/v1/payments/checkout"), home.tenant(), """
                        {"leaseId":"%s","chargeIds":["%s"]}""".formatted(home.leaseId(), JsonPath.read(charge, "$.id")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("amount_too_small"));
        RAZORPAY.verify(0, postRequestedFor(urlEqualTo("/v1/orders")));
    }

    @Test
    void rejectedKeysAreTheServersProblemNotTheTenantsSession() throws Exception {
        RAZORPAY.stubFor(post("/v1/orders").willReturn(aResponse().withStatus(401)
                .withBody("{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Authentication failed\"}}")));
        Tenancy home = moveIn(true);

        flows.authed(mvcPost("/api/v1/payments/checkout"), home.tenant(), checkoutBody(home))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("payments_misconfigured"));
    }

    @Test
    void checkoutWaitsForTheLandlordsPayoutAccount() throws Exception {
        Tenancy home = moveIn(false);
        flows.get(home.tenant(), "/api/v1/dashboard/tenant").andExpect(jsonPath("$.payOnline").value(false));
        flows.authed(mvcPost("/api/v1/payments/checkout"), home.tenant(), checkoutBody(home))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("landlord_not_ready"));
    }

    @Test
    void onlyTheSignedWebhookMarksRentPaidAndEachEventCountsOnce() throws Exception {
        Tenancy home = moveIn(true);
        Started payment = checkout(home);
        String rzpPayment = "pay_" + suffix();
        String paid = orderPaid(payment.orderId(), rzpPayment, 3_750_000);

        webhook(paid, "evt_" + suffix(), "not-the-signature").andExpect(status().isBadRequest());
        flows.get(home.tenant(), home.ledgerPath()).andExpect(jsonPath("$.outstandingPaise").value(3_750_000));

        String eventId = "evt_" + suffix();
        webhook(paid, eventId, sign(paid)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PROCESSED"));
        webhook(paid, eventId, sign(paid)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("DUPLICATE"));
        // payment.captured reports the same success and finds the payment already confirmed.
        String captured = paymentEvent("payment.captured", rzpPayment, payment.orderId(), 3_750_000, "captured", null);
        webhook(captured, "evt_" + suffix(), sign(captured)).andExpect(jsonPath("$.outcome").value("PROCESSED"));

        flows.get(home.tenant(), home.ledgerPath())
                .andExpect(jsonPath("$.outstandingPaise").value(0))
                .andExpect(jsonPath("$.entries[*].status", everyItem(is("PAID"))));
        flows.get(home.tenant(), "/api/v1/payments/" + payment.paymentId())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
        flows.get(home.landlord(), home.receiptsPath())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value("0001"))
                .andExpect(jsonPath("$[0].amountPaise").value(3_750_000))
                .andExpect(jsonPath("$[0].paymentReference").value(rzpPayment));
        flows.get(home.tenant(), "/api/v1/dashboard/tenant")
                .andExpect(jsonPath("$.outstandingPaise").value(0))
                .andExpect(jsonPath("$.pendingPayment").isEmpty())
                .andExpect(jsonPath("$.lastReceipt.number").value("0001"));

        String transfer = """
                {"entity":"event","event":"transfer.processed","contains":["transfer"],
                 "payload":{"transfer":{"entity":{"id":"trf_%s","entity":"transfer","source":"%s",
                  "recipient":"%s","amount":3675000,"currency":"INR"}}},"created_at":1788000000}
                """.formatted(suffix(), rzpPayment, home.account());
        webhook(transfer, "evt_" + suffix(), sign(transfer)).andExpect(jsonPath("$.outcome").value("PROCESSED"));
        assertThat(jdbc.queryForObject("select transfer_status from payments where id = ?", String.class,
                UUID.fromString(payment.paymentId()))).isEqualTo("processed");

        String ledgerBody = flows.get(home.tenant(), home.ledgerPath()).andReturn().getResponse().getContentAsString();
        List<String> chargeIds = JsonPath.read(ledgerBody, "$.entries[*].id");
        flows.authed(mvcPost("/api/v1/payments/checkout"), home.tenant(), """
                        {"leaseId":"%s","chargeIds":["%s"]}""".formatted(home.leaseId(), chargeIds.getFirst()))
                .andExpect(status().isConflict());
    }

    @Test
    void aConfirmationForTheWrongAmountIsNotTrusted() throws Exception {
        Tenancy home = moveIn(true);
        Started payment = checkout(home);
        String paid = orderPaid(payment.orderId(), "pay_" + suffix(), 100);

        webhook(paid, "evt_" + suffix(), sign(paid)).andExpect(jsonPath("$.outcome").value("IGNORED"));
        flows.get(home.tenant(), home.ledgerPath()).andExpect(jsonPath("$.outstandingPaise").value(3_750_000));
        flows.get(home.tenant(), "/api/v1/payments/" + payment.paymentId())
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void aFailedPaymentLeavesTheRentDue() throws Exception {
        Tenancy home = moveIn(true);
        Started payment = checkout(home);
        String failed = paymentEvent("payment.failed", "pay_" + suffix(), payment.orderId(), 3_750_000, "failed",
                "The bank declined this payment.");

        webhook(failed, "evt_" + suffix(), sign(failed)).andExpect(jsonPath("$.outcome").value("PROCESSED"));
        flows.get(home.tenant(), "/api/v1/payments/" + payment.paymentId())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("The bank declined this payment."));
        flows.get(home.tenant(), home.ledgerPath()).andExpect(jsonPath("$.outstandingPaise").value(3_750_000));
    }

    @Test
    void bothPartiesDownloadTheReceiptAndNobodyElseCan() throws Exception {
        Tenancy home = moveIn(true);
        Tenancy other = moveIn(true);
        Started payment = checkout(home);
        String rzpPayment = "pay_" + suffix();
        String paid = orderPaid(payment.orderId(), rzpPayment, 3_750_000);
        webhook(paid, "evt_" + suffix(), sign(paid)).andExpect(jsonPath("$.outcome").value("PROCESSED"));
        String receiptId = JsonPath.read(flows.get(home.tenant(), home.receiptsPath())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        String pdfPath = "/api/v1/receipts/" + receiptId + "/pdf";

        byte[] pdf = flows.get(home.tenant(), pdfPath)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("rentbook-receipt-0001.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        // Lines wrap wherever the page runs out, so compare with the whitespace folded.
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1).replaceAll("\\s+", " ");
        assertThat(text).contains("Rent receipt", "No. 0001", "Asha Rao", "Lata Iyer", "37,500",
                "Rupees Thirty Seven Thousand Five Hundred only", "Security deposit", "Rent for ",
                "14 Koramangala 5th Block", "Bengaluru 560095", rzpPayment, "Paid");

        flows.get(home.landlord(), pdfPath).andExpect(status().isOk());
        flows.get(other.tenant(), pdfPath).andExpect(status().isNotFound());
        flows.get(other.landlord(), pdfPath).andExpect(status().isNotFound());
        flows.get(other.tenant(), home.receiptsPath()).andExpect(status().isNotFound());
    }

    private Tenancy moveIn(boolean payoutsReady) throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        String pg = flows.createProperty(landlord, "Payments PG");
        String flat = flows.addUnit(landlord, pg, "FLAT", "Flat 4A", null);
        Flows.Session tenant = flows.acceptAsNewTenant(
                flows.inviteToken(landlord, flat, Flows.email("asha"), ledger.today()));
        String account = null;
        if (payoutsReady) {
            account = "acc_" + suffix();
            payouts.linkExisting(UUID.fromString(landlord.userId()), account, "Lata Iyer");
        }
        return new Tenancy(landlord, tenant, flows.leaseIdFor(tenant), account);
    }

    private Started checkout(Tenancy home) throws Exception {
        String body = flows.authed(mvcPost("/api/v1/payments/checkout"), home.tenant(), checkoutBody(home))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value(KEY_ID))
                .andExpect(jsonPath("$.prefill.email").exists())
                .andReturn().getResponse().getContentAsString();
        return new Started(JsonPath.read(body, "$.paymentId"), JsonPath.read(body, "$.orderId"),
                ((Number) JsonPath.read(body, "$.amountPaise")).longValue());
    }

    private static String checkoutBody(Tenancy home) {
        return "{\"leaseId\":\"%s\",\"chargeIds\":[]}".formatted(home.leaseId());
    }

    private ResultActions callback(Tenancy home, Started payment, String rzpPaymentId, String signature)
            throws Exception {
        return flows.authed(mvcPost("/api/v1/payments/" + payment.paymentId() + "/client-callback"), home.tenant(), """
                {"razorpayOrderId":"%s","razorpayPaymentId":"%s","razorpaySignature":"%s"}
                """.formatted(payment.orderId(), rzpPaymentId, signature));
    }

    private ResultActions webhook(String body, String eventId, String signature) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/webhooks/razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", signature)
                .header("x-razorpay-event-id", eventId)
                .content(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sign(String body) {
        return Signatures.hmacHex(body.getBytes(StandardCharsets.UTF_8), WEBHOOK_SECRET);
    }

    private static String orderPaid(String orderId, String paymentId, long amountPaise) {
        return """
                {"entity":"event","account_id":"acc_platform","event":"order.paid","contains":["payment","order"],
                 "payload":{
                  "payment":{"entity":{"id":"%s","entity":"payment","amount":%d,"currency":"INR","status":"captured",
                   "order_id":"%s","method":"upi"}},
                  "order":{"entity":{"id":"%s","entity":"order","amount":%d,"amount_paid":%d,"status":"paid"}}},
                 "created_at":1788000000}
                """.formatted(paymentId, amountPaise, orderId, orderId, amountPaise, amountPaise);
    }

    private static String paymentEvent(String event, String paymentId, String orderId, long amountPaise,
                                       String paymentStatus, String error) {
        return """
                {"entity":"event","account_id":"acc_platform","event":"%s","contains":["payment"],
                 "payload":{"payment":{"entity":{"id":"%s","entity":"payment","amount":%d,"currency":"INR",
                  "status":"%s","order_id":"%s","method":"upi","error_description":%s}}},
                 "created_at":1788000000}
                """.formatted(event, paymentId, amountPaise, paymentStatus, orderId,
                error == null ? "null" : "\"" + error + "\"");
    }

    private static void stubOnboarding(String account) {
        RAZORPAY.stubFor(post("/v2/accounts").willReturn(okJson("""
                {"id":"%s","type":"route","status":"created"}""".formatted(account))));
        RAZORPAY.stubFor(post("/v2/accounts/" + account + "/stakeholders").willReturn(okJson("""
                {"id":"sth_%s","entity":"stakeholder"}""".formatted(suffix()))));
        RAZORPAY.stubFor(post("/v2/accounts/" + account + "/products").willReturn(okJson("""
                {"id":"acc_prd_1","activation_status":"needs_clarification"}""")));
        RAZORPAY.stubFor(patch(urlEqualTo("/v2/accounts/" + account + "/products/acc_prd_1")).willReturn(okJson("""
                {"id":"acc_prd_1","activation_status":"activated"}""")));
    }

    private static MockHttpServletRequestBuilder mvcPost(String path) {
        return MockMvcRequestBuilders.post(path);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }
}
