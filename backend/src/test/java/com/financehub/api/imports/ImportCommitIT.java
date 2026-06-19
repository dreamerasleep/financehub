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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class ImportCommitIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String register(String email) throws Exception {
        String resp = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "name", "Tester", "password", "Tester1234!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
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
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private long uploadGoodFile(String bearer) throws Exception {
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,INCOME,主帳戶,30000.00,薪資,,六月薪水\n"
                + "2026-06-02,EXPENSE,主帳戶,250.50,飲食,,午餐\n"
                + "2026-06-03,TRANSFER,主帳戶,5000.00,,副帳戶,搬錢\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "good.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String resp = mockMvc.perform(multipart("/api/v1/imports")
                        .file(file).header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private double balance(String bearer, Long accId) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/accounts/" + accId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("currentBalance").asDouble();
    }

    @Test
    void commitInsertsTransactionsAndAdjustsBalances() throws Exception {
        String bearer = register("c1+" + System.nanoTime() + "@example.com");
        Long from = createAccount(bearer, "主帳戶", "TWD", "10000.00");
        Long to = createAccount(bearer, "副帳戶", "TWD", "0.00");

        long jobId = uploadGoodFile(bearer);

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committedCount").value(3));

        org.assertj.core.api.Assertions.assertThat(balance(bearer, from)).isEqualTo(34749.50);
        org.assertj.core.api.Assertions.assertThat(balance(bearer, to)).isEqualTo(5000.00);
    }

    @Test
    void commitOnlySelectedRows() throws Exception {
        String bearer = register("c2+" + System.nanoTime() + "@example.com");
        Long acc = createAccount(bearer, "主帳戶", "TWD", "0.00");
        createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        String detail = mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long firstRowId = -1;
        for (JsonNode r : objectMapper.readTree(detail).get("rows")) {
            if ("OK".equals(r.get("status").asText())) {
                firstRowId = r.get("id").asLong();
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(firstRowId).isNotEqualTo(-1);

        Map<String, Object> body = Map.of("rowIds", List.of(firstRowId));
        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committedCount").value(1));

        org.assertj.core.api.Assertions.assertThat(balance(bearer, acc)).isEqualTo(30000.00);
    }

    @Test
    void commitTwiceReturnsConflict() throws Exception {
        String bearer = register("c3+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void commitWithDeletedAccountReResolvesAndReturnsConflict() throws Exception {
        String bearer = register("c4+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        Long to = createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        mockMvc.perform(delete("/api/v1/accounts/" + to)
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.financehub.application.imports.ImportExpiryJob expiryJob;

    @Test
    void expirySchedulerMarksStaleJobsExpired() throws Exception {
        String bearer = register("c6+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        expiryJob.expireOnce(java.time.OffsetDateTime.now().plusHours(25));

        mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("EXPIRED"));
    }

    @Test
    void cancelMovesJobToCancelledAndBlocksCommit() throws Exception {
        String bearer = register("c5+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        createAccount(bearer, "副帳戶", "TWD", "0.00");
        long jobId = uploadGoodFile(bearer);

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/cancel")
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/commit")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }
}
