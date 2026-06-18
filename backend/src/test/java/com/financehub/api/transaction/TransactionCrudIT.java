package com.financehub.api.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financehub.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class TransactionCrudIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken(String email) throws Exception {
        String resp = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "name", "Tester",
                                "password", "Tester1234!"
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("token").asText();
    }

    private Long createAccount(String bearer, String name, String initialBalance) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", "SAVING");
        body.put("currency", "TWD");
        body.put("initialBalance", initialBalance);
        String resp = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private long pickCategoryId(String bearer, String kind) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode node : objectMapper.readTree(resp)) {
            if (kind.equals(node.get("kind").asText())) {
                return node.get("id").asLong();
            }
        }
        throw new AssertionError("No category of kind " + kind);
    }

    @Test
    void listsSystemCategoriesAfterRegister() throws Exception {
        String bearer = "Bearer " + registerAndGetToken("cat+" + System.nanoTime() + "@example.com");
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[?(@.system == true)].name").isNotEmpty());
    }

    @Test
    void createTransactionUpdatesBalance() throws Exception {
        String bearer = "Bearer " + registerAndGetToken("txn+" + System.nanoTime() + "@example.com");
        Long accountId = createAccount(bearer, "主帳戶", "10000.00");
        long expenseCat = pickCategoryId(bearer, "EXPENSE");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId,
                                "categoryId", expenseCat,
                                "type", "EXPENSE",
                                "amount", "350.00",
                                "txnDate", "2026-06-18",
                                "note", "午餐"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(350.00));

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(9650.00));
    }

    @Test
    void updateAndDeleteRollbackBalance() throws Exception {
        String bearer = "Bearer " + registerAndGetToken("txn2+" + System.nanoTime() + "@example.com");
        Long accountId = createAccount(bearer, "主帳戶", "5000.00");
        long incomeCat = pickCategoryId(bearer, "INCOME");
        long expenseCat = pickCategoryId(bearer, "EXPENSE");

        String createResp = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId,
                                "categoryId", incomeCat,
                                "type", "INCOME",
                                "amount", "1000.00",
                                "txnDate", "2026-06-18"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long txnId = objectMapper.readTree(createResp).get("id").asLong();

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", bearer))
                .andExpect(jsonPath("$.currentBalance").value(6000.00));

        mockMvc.perform(put("/api/v1/transactions/" + txnId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId,
                                "categoryId", expenseCat,
                                "type", "EXPENSE",
                                "amount", "200.00",
                                "txnDate", "2026-06-18"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", bearer))
                .andExpect(jsonPath("$.currentBalance").value(4800.00));

        mockMvc.perform(delete("/api/v1/transactions/" + txnId)
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", bearer))
                .andExpect(jsonPath("$.currentBalance").value(5000.00));
    }

    @Test
    void categoryKindMustMatchTransactionType() throws Exception {
        String bearer = "Bearer " + registerAndGetToken("mismatch+" + System.nanoTime() + "@example.com");
        Long accountId = createAccount(bearer, "主帳戶", "1000.00");
        long incomeCat = pickCategoryId(bearer, "INCOME");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", accountId,
                                "categoryId", incomeCat,
                                "type", "EXPENSE",
                                "amount", "100.00",
                                "txnDate", "2026-06-18"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherUserCannotAccessTransaction() throws Exception {
        String aliceBearer = "Bearer " + registerAndGetToken("a+" + System.nanoTime() + "@example.com");
        String bobBearer   = "Bearer " + registerAndGetToken("b+" + System.nanoTime() + "@example.com");

        Long aliceAcc = createAccount(aliceBearer, "Alice 主帳", "1000.00");
        long aliceCat = pickCategoryId(aliceBearer, "EXPENSE");

        String createResp = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", aliceBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", aliceAcc,
                                "categoryId", aliceCat,
                                "type", "EXPENSE",
                                "amount", "100.00",
                                "txnDate", "2026-06-18"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long txnId = objectMapper.readTree(createResp).get("id").asLong();

        mockMvc.perform(get("/api/v1/transactions/" + txnId)
                        .header("Authorization", bobBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIs401() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized());
    }
}
