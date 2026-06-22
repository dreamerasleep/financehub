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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class ImportRowPatchIT {

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

    private long uploadCsv(String bearer, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        String resp = mockMvc.perform(multipart("/api/v1/imports")
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private JsonNode getJob(String bearer, long jobId) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/imports/" + jobId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp);
    }

    private long findRowIdByIndex(JsonNode jobDetail, int rowIndex) {
        for (JsonNode r : jobDetail.get("rows")) {
            if (r.get("rowIndex").asInt() == rowIndex) {
                return r.get("id").asLong();
            }
        }
        throw new AssertionError("No row with index " + rowIndex);
    }

    private String patchBody(String date, String type, String account, String amount,
                             String category, String toAccount, String note) throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("type", type);
        m.put("account", account);
        m.put("amount", amount);
        m.put("category", category);
        m.put("to_account", toAccount);
        m.put("note", note);
        return objectMapper.writeValueAsString(m);
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

    @Test
    void patchErrorRowWithValidFieldsBecomesOk() throws Exception {
        String bearer = register("p1+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");

        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n"
                + "2026-06-02,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(bearer, csv);

        JsonNode before = getJob(bearer, jobId);
        long badRowId = findRowIdByIndex(before, 2);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + badRowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-02", "EXPENSE", "主帳戶", "200.00",
                                "飲食", "", "晚餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("OK"))
                .andExpect(jsonPath("$.row.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.job.okCount").value(2))
                .andExpect(jsonPath("$.job.errorCount").value(0))
                .andExpect(jsonPath("$.job.dupCount").value(0));
    }

    @Test
    void patchErrorRowMatchingExistingTransactionBecomesDuplicate() throws Exception {
        String bearer = register("p2+" + System.nanoTime() + "@example.com");
        Long accId = createAccount(bearer, "主帳戶", "TWD", "0.00");
        Long catFood = pickCategoryByNameKind(bearer, "飲食", "EXPENSE");
        Map<String, Object> txn = new HashMap<>();
        txn.put("accountId", accId);
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

        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-02,EXPENSE,主帳戶,abc,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        JsonNode before = getJob(bearer, jobId);
        long rowId = findRowIdByIndex(before, 1);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-02", "EXPENSE", "主帳戶", "250.50",
                                "飲食", "", "午餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.job.dupCount").value(1))
                .andExpect(jsonPath("$.job.errorCount").value(0));
    }

    @Test
    void patchDuplicateRowWithUniqueAmountBecomesOk() throws Exception {
        String bearer = register("p3+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");

        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        JsonNode before = getJob(bearer, jobId);
        long dupRowId = findRowIdByIndex(before, 2);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + dupRowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "120.00",
                                "飲食", "", "午餐 2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("OK"))
                .andExpect(jsonPath("$.job.okCount").value(2))
                .andExpect(jsonPath("$.job.dupCount").value(0));
    }

    @Test
    void patchOkRowReturns403() throws Exception {
        String bearer = register("p4+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        long rowId = findRowIdByIndex(getJob(bearer, jobId), 1);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "200.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ok_row_not_editable"));
    }

    @Test
    void patchUnrelatedRowIdReturns404() throws Exception {
        String bearer = register("p5+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(bearer, csv);

        long fakeRowId = 9_999_999L;
        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + fakeRowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchOtherUsersJobReturns404() throws Exception {
        String alice = register("alice+" + System.nanoTime() + "@example.com");
        String bob = register("bob+" + System.nanoTime() + "@example.com");
        createAccount(alice, "主帳戶", "TWD", "0.00");

        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(alice, csv);
        long rowId = findRowIdByIndex(getJob(alice, jobId), 1);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchCancelledJobReturns409() throws Exception {
        String bearer = register("p7+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(bearer, csv);
        long rowId = findRowIdByIndex(getJob(bearer, jobId), 1);

        mockMvc.perform(post("/api/v1/imports/" + jobId + "/cancel")
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("job_not_pending"));
    }

    @Test
    void patchWithMissingDateBecomesError() throws Exception {
        String bearer = register("p8+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,abc,飲食,,壞列\n";
        long jobId = uploadCsv(bearer, csv);
        long rowId = findRowIdByIndex(getJob(bearer, jobId), 1);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("ERROR"))
                .andExpect(jsonPath("$.row.errorMessage").value("Date is required"))
                .andExpect(jsonPath("$.job.errorCount").value(1));
    }

    @Test
    void patchOkRowAmountDoesNotRetroactivelyFlipDupRow() throws Exception {
        String bearer = register("p9+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        JsonNode before = getJob(bearer, jobId);
        long rowAId = findRowIdByIndex(before, 1);
        long rowBId = findRowIdByIndex(before, 2);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowBId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "150.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("OK"));

        JsonNode after = getJob(bearer, jobId);
        for (JsonNode r : after.get("rows")) {
            if (r.get("id").asLong() == rowAId) {
                org.junit.jupiter.api.Assertions.assertEquals("OK", r.get("status").asText());
            }
        }
    }

    @Test
    void countersStayConsistentAfterPatch() throws Exception {
        String bearer = register("p10+" + System.nanoTime() + "@example.com");
        createAccount(bearer, "主帳戶", "TWD", "0.00");
        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n"
                + "2026-06-02,EXPENSE,主帳戶,abc,飲食,,壞\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        long badRowId = findRowIdByIndex(getJob(bearer, jobId), 2);

        String resp = mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + badRowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-03", "EXPENSE", "主帳戶", "50.00",
                                "飲食", "", "點心")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(resp);
        int ok = root.path("job").path("okCount").asInt();
        int err = root.path("job").path("errorCount").asInt();
        int dup = root.path("job").path("dupCount").asInt();
        int total = root.path("job").path("rowCount").asInt();
        org.junit.jupiter.api.Assertions.assertEquals(total, ok + err + dup);
    }

    @Test
    void patchDuplicateRowWithSameContentStaysDup() throws Exception {
        String bearer = register("p11+" + System.nanoTime() + "@example.com");
        Long accId = createAccount(bearer, "主帳戶", "TWD", "0.00");
        Long catFood = pickCategoryByNameKind(bearer, "飲食", "EXPENSE");
        Map<String, Object> txn = new HashMap<>();
        txn.put("accountId", accId);
        txn.put("categoryId", catFood);
        txn.put("type", "EXPENSE");
        txn.put("amount", "100.00");
        txn.put("txnDate", "2026-06-01");
        txn.put("note", "午餐");
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn)))
                .andExpect(status().isCreated());

        String csv = "date,type,account,amount,category,to_account,note\n"
                + "2026-06-01,EXPENSE,主帳戶,100.00,飲食,,午餐\n";
        long jobId = uploadCsv(bearer, csv);
        long rowId = findRowIdByIndex(getJob(bearer, jobId), 1);

        mockMvc.perform(patch("/api/v1/imports/" + jobId + "/rows/" + rowId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody("2026-06-01", "EXPENSE", "主帳戶", "100.00",
                                "飲食", "", "午餐")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("DUPLICATE"));
    }
}
