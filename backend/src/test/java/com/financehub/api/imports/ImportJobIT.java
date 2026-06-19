package com.financehub.api.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financehub.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class ImportJobIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String resp = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "name", "Tester", "password", "Tester1234!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(resp).get("token").asText();
    }

    private Long createAccount(String bearer, String name, String currency, String balance) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", "SAVING");
        body.put("currency", currency);
        body.put("initialBalance", balance);
        String resp = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private byte[] readFixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/imports/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void uploadParsesMixedFileAndCountsCorrectly() throws Exception {
        String bearer = register("imp+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        Long catFood = pickCategoryByNameKind(bearer, "飲食", "EXPENSE");
        Long acc = lookupAccountId(bearer, "主帳戶");
        Map<String, Object> txn = new HashMap<>();
        txn.put("accountId", acc);
        txn.put("categoryId", catFood);
        txn.put("type", "EXPENSE");
        txn.put("amount", "250.50");
        txn.put("txnDate", "2026-06-02");
        txn.put("note", "午餐");
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn)))
                .andExpect(status().isCreated());

        byte[] bytes = readFixture("sample-mixed.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample-mixed.csv", "text/csv", bytes);

        String resp = mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rowCount").value(6))
                .andExpect(jsonPath("$.errorCount").value(2))
                .andExpect(jsonPath("$.dupCount").value(2))
                .andExpect(jsonPath("$.okCount").value(2))
                .andReturn().getResponse().getContentAsString();
        long jobId = objectMapper.readTree(resp).get("id").asLong();

        mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.id").value(jobId))
                .andExpect(jsonPath("$.rows.length()").value(6));
    }

    @Test
    void unsupportedExtensionReturns415() throws Exception {
        String bearer = register("ext+" + System.nanoTime() + "@example.com");
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void getOtherUsersJobReturns404() throws Exception {
        String alice = register("alice+" + System.nanoTime() + "@example.com");
        String bob = register("bob+" + System.nanoTime() + "@example.com");
        createAccount(alice, "主帳戶", "TWD", "0.00");

        byte[] bytes = readFixture("sample-mixed.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample-mixed.csv", "text/csv", bytes);
        String resp = mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", alice))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long jobId = objectMapper.readTree(resp).get("id").asLong();

        mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.csv", "text/csv", "date,type,account,amount\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/imports").file(file))
                .andExpect(status().isUnauthorized());
    }

    private Long pickCategoryByNameKind(String bearer, String name, String kind) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode node : objectMapper.readTree(resp)) {
            if (name.equals(node.get("name").asText()) && kind.equals(node.get("kind").asText())) {
                return node.get("id").asLong();
            }
        }
        throw new AssertionError("No category " + name + "/" + kind);
    }

    private Long lookupAccountId(String bearer, String name) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode node : objectMapper.readTree(resp)) {
            if (name.equals(node.get("name").asText())) {
                return node.get("id").asLong();
            }
        }
        throw new AssertionError("Account " + name + " not found");
    }
}
