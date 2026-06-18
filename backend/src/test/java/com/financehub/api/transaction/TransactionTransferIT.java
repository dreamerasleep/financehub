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
class TransactionTransferIT {

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

    private Long createAccount(String bearer, String name, String currency, String initialBalance) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", "SAVING");
        body.put("currency", currency);
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

    private void assertBalance(String bearer, Long accountId, double expected) throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(expected));
    }

    @Test
    void transferUpdatesBothBalances() throws Exception {
        String bearer = "Bearer " + registerAndGetToken("tx+" + System.nanoTime() + "@example.com");
        Long from = createAccount(bearer, "主帳戶",   "TWD", "5000.00");
        Long to   = createAccount(bearer, "副帳戶",   "TWD", "2000.00");

        Map<String, Object> body = new HashMap<>();
        body.put("accountId", from);
        body.put("toAccountId", to);
        body.put("type", "TRANSFER");
        body.put("amount", "1500.00");
        body.put("txnDate", "2026-06-18");
        body.put("note", "月初轉帳");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.toAccountId").value(to))
                .andExpect(jsonPath("$.categoryId").doesNotExist());

        assertBalance(bearer, from, 3500.00);
        assertBalance(bearer, to,   3500.00);
    }

    @Test
    void updateAndDeleteTransferRollbackBothSides() throws Exception {
        String bearer = "Bearer " + registerAndGetToken("tx2+" + System.nanoTime() + "@example.com");
        Long a = createAccount(bearer, "A", "TWD", "10000.00");
        Long b = createAccount(bearer, "B", "TWD", "0.00");
        Long c = createAccount(bearer, "C", "TWD", "0.00");

        Map<String, Object> create = new HashMap<>();
        create.put("accountId", a);
        create.put("toAccountId", b);
        create.put("type", "TRANSFER");
        create.put("amount", "3000.00");
        create.put("txnDate", "2026-06-18");

        String resp = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long txnId = objectMapper.readTree(resp).get("id").asLong();

        assertBalance(bearer, a, 7000.00);
        assertBalance(bearer, b, 3000.00);
        assertBalance(bearer, c, 0.00);

        Map<String, Object> update = new HashMap<>();
        update.put("accountId", a);
        update.put("toAccountId", c);
        update.put("type", "TRANSFER");
        update.put("amount", "1000.00");
        update.put("txnDate", "2026-06-18");

        mockMvc.perform(put("/api/v1/transactions/" + txnId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        assertBalance(bearer, a, 9000.00);
        assertBalance(bearer, b, 0.00);
        assertBalance(bearer, c, 1000.00);

        mockMvc.perform(delete("/api/v1/transactions/" + txnId)
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        assertBalance(bearer, a, 10000.00);
        assertBalance(bearer, b, 0.00);
        assertBalance(bearer, c, 0.00);
    }

    @Test
    void rejectsTransferToSameAccount() throws Exception {
        String bearer = "Bearer " + registerAndGetToken("same+" + System.nanoTime() + "@example.com");
        Long a = createAccount(bearer, "A", "TWD", "1000.00");

        Map<String, Object> body = new HashMap<>();
        body.put("accountId", a);
        body.put("toAccountId", a);
        body.put("type", "TRANSFER");
        body.put("amount", "100.00");
        body.put("txnDate", "2026-06-18");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCrossCurrencyTransfer() throws Exception {
        String bearer = "Bearer " + registerAndGetToken("fx+" + System.nanoTime() + "@example.com");
        Long twd = createAccount(bearer, "TWD 帳", "TWD", "1000.00");
        Long usd = createAccount(bearer, "USD 帳", "USD", "0.00");

        Map<String, Object> body = new HashMap<>();
        body.put("accountId", twd);
        body.put("toAccountId", usd);
        body.put("type", "TRANSFER");
        body.put("amount", "100.00");
        body.put("txnDate", "2026-06-18");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTransferWithCategory() throws Exception {
        String bearer = "Bearer " + registerAndGetToken("cat+" + System.nanoTime() + "@example.com");
        Long a = createAccount(bearer, "A", "TWD", "1000.00");
        Long b = createAccount(bearer, "B", "TWD", "0.00");
        long someCat = pickCategoryId(bearer, "EXPENSE");

        Map<String, Object> body = new HashMap<>();
        body.put("accountId", a);
        body.put("toAccountId", b);
        body.put("categoryId", someCat);
        body.put("type", "TRANSFER");
        body.put("amount", "100.00");
        body.put("txnDate", "2026-06-18");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTransferToAnotherUsersAccount() throws Exception {
        String alice = "Bearer " + registerAndGetToken("alice+" + System.nanoTime() + "@example.com");
        String bob   = "Bearer " + registerAndGetToken("bob+"   + System.nanoTime() + "@example.com");
        Long aliceAcc = createAccount(alice, "Alice", "TWD", "1000.00");
        Long bobAcc   = createAccount(bob,   "Bob",   "TWD", "0.00");

        Map<String, Object> body = new HashMap<>();
        body.put("accountId", aliceAcc);
        body.put("toAccountId", bobAcc);
        body.put("type", "TRANSFER");
        body.put("amount", "100.00");
        body.put("txnDate", "2026-06-18");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }
}
