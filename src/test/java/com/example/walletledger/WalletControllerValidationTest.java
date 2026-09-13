package com.example.walletledger;

import com.example.walletledger.repository.LedgerTransactionRepository;
import com.example.walletledger.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletControllerValidationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WalletRepository wallets;
    @Autowired LedgerTransactionRepository transactions;

    @BeforeEach
    void setup() throws Exception {
        transactions.deleteAll();
        wallets.deleteAll();
        mvc.perform(post("/api/v1/wallets/player-1")).andExpect(status().isCreated());
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        var body = Map.of(
                "requestId", "bad-amount",
                "amount", "-1.00",
                "reason", "invalid",
                "referenceId", "r1"
        );

        mvc.perform(post("/api/v1/wallets/player-1/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("amount")));
    }

    @Test
    void rejectsBlankReason() throws Exception {
        var body = Map.of(
                "requestId", "bad-reason",
                "amount", "1.00",
                "reason", "",
                "referenceId", "r2"
        );

        mvc.perform(post("/api/v1/wallets/player-1/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("reason")));
    }

    @Test
    void returnsNotFoundForUnknownWallet() throws Exception {
        mvc.perform(get("/api/v1/wallets/missing/balance"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDebitWhenBalanceIsInsufficient() throws Exception {
        var body = Map.of(
                "requestId", "overspend",
                "amount", "5.00",
                "reason", "purchase",
                "referenceId", "order-1"
        );

        mvc.perform(post("/api/v1/wallets/player-1/debits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Insufficient funds")));
    }

    @Test
    void refundsARealDebit() throws Exception {
        var debit = Map.of(
                "requestId", "debit-1",
                "amount", "10.00",
                "reason", "purchase",
                "referenceId", "order-1"
        );
        var refund = Map.of(
                "requestId", "refund-1",
                "originalRequestId", "debit-1"
        );

        mvc.perform(post("/api/v1/wallets/player-1/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "seed-1",
                                "amount", "10.00",
                                "reason", "seed",
                                "referenceId", "seed"
                        ))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/wallets/player-1/debits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debit)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/wallets/player-1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("refund-1"))
                .andExpect(jsonPath("$.referenceId").value("debit-1"));

        mvc.perform(get("/api/v1/wallets/player-1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10.00));
    }

    @Test
    void transfersCurrencyBetweenPlayers() throws Exception {
        mvc.perform(post("/api/v1/wallets/player-1/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestId", "seed-transfer",
                                "amount", "25.00",
                                "reason", "seed",
                                "referenceId", "seed"
                        ))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/wallets/player-2"))
                .andExpect(status().isCreated());

        var transfer = Map.of(
                "requestId", "transfer-1",
                "toPlayerId", "player-2",
                "amount", "8.00",
                "reason", "gift",
                "referenceId", "gift-9"
        );

        mvc.perform(post("/api/v1/wallets/player-1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debit.requestId").value("transfer-1:debit"))
                .andExpect(jsonPath("$.credit.requestId").value("transfer-1:credit"));

        mvc.perform(get("/api/v1/wallets/player-1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(17.00));

        mvc.perform(get("/api/v1/wallets/player-2/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(8.00));
    }

    @Test
    void refundingMissingDebitReturnsBadRequest() throws Exception {
        var refund = Map.of(
                "requestId", "refund-missing",
                "originalRequestId", "missing"
        );

        mvc.perform(post("/api/v1/wallets/player-1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Original transaction not found")));
    }

    @Test
    void transferToSelfIsRejected() throws Exception {
        var transfer = Map.of(
                "requestId", "transfer-self",
                "toPlayerId", "player-1",
                "amount", "1.00",
                "reason", "bad",
                "referenceId", "gift-1"
        );

        mvc.perform(post("/api/v1/wallets/player-1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must be different")));
    }

    @Test
    void claimsRewardIntoWallet() throws Exception {
        var body = Map.of(
                "requestId", "claim-1",
                "rewardId", "reward-42",
                "amount", "15.00",
                "reason", "daily quest",
                "referenceId", "quest-9"
        );

        mvc.perform(post("/api/v1/wallets/player-1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("claim-1"))
                .andExpect(jsonPath("$.referenceId").value("reward-42"));

        mvc.perform(get("/api/v1/wallets/player-1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(15.00));
    }

    @Test
    void reusingRequestIdWithDifferentPayloadReturnsUnprocessableEntity() throws Exception {
        var first = Map.of(
                "requestId", "same-key",
                "amount", "10.00",
                "reason", "reward",
                "referenceId", "mission-1"
        );
        var second = Map.of(
                "requestId", "same-key",
                "amount", "20.00",
                "reason", "reward",
                "referenceId", "mission-1"
        );

        mvc.perform(post("/api/v1/wallets/player-1/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/wallets/player-1/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isUnprocessableEntity());
    }
}
