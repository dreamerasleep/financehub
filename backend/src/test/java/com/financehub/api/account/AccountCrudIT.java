package com.financehub.api.account;

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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class AccountCrudIT {

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

    @Test
    void createListUpdateDeleteAccount() throws Exception {
        String token = registerAndGetToken("acc+" + System.nanoTime() + "@example.com");
        String bearer = "Bearer " + token;

        String createResp = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "玉山活存",
                                "type", "SAVING",
                                "currency", "TWD",
                                "initialBalance", "10000.00"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("玉山活存"))
                .andExpect(jsonPath("$.type").value("SAVING"))
                .andExpect(jsonPath("$.currentBalance").value(10000.00))
                .andReturn().getResponse().getContentAsString();
        Long accountId = objectMapper.readTree(createResp).get("id").asLong();

        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(accountId));

        mockMvc.perform(put("/api/v1/accounts/" + accountId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "玉山活存(更名)",
                                "type", "CHECKING",
                                "currency", "TWD",
                                "currentBalance", "8500.50"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("玉山活存(更名)"))
                .andExpect(jsonPath("$.type").value("CHECKING"))
                .andExpect(jsonPath("$.currentBalance").value(8500.50));

        mockMvc.perform(delete("/api/v1/accounts/" + accountId)
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUserCannotSeeAccount() throws Exception {
        String aliceToken = registerAndGetToken("a+" + System.nanoTime() + "@example.com");
        String bobToken = registerAndGetToken("b+" + System.nanoTime() + "@example.com");

        String createResp = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Alice 帳戶",
                                "type", "CASH",
                                "currency", "TWD"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long accountId = objectMapper.readTree(createResp).get("id").asLong();

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIs401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }
}
