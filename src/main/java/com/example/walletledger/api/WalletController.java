package com.example.walletledger.api;

import com.example.walletledger.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets")
@Validated
@Tag(name = "Wallet", description = "Wallet operations and immutable ledger history")
public class WalletController {
    private final WalletService service;
    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping("/{playerId}")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Create wallet", description = "Creates an empty wallet if it does not exist")
    public WalletDtos.WalletResponse create(@PathVariable @NotBlank String playerId) {
        return service.createWallet(playerId);
    }

    @PostMapping("/{playerId}/credits")
    @Operation(summary = "Credit wallet")
    public WalletDtos.TransactionResponse credit(@PathVariable @NotBlank String playerId,
                                                 @Valid @RequestBody WalletDtos.OperationRequest request) {
        return service.credit(playerId, request);
    }
    @PostMapping("/{playerId}/debits")
    @Operation(summary = "Debit wallet")
    public WalletDtos.TransactionResponse debit(@PathVariable @NotBlank String playerId,
                                                @Valid @RequestBody WalletDtos.OperationRequest request) {
        return service.debit(playerId, request);
    }
    @PostMapping("/{playerId}/transfers")
    @Operation(summary = "Transfer currency to another player")
    public WalletDtos.TransferResponse transfer(@PathVariable @NotBlank String playerId,
                                                @Valid @RequestBody WalletDtos.TransferRequest request) {
        return service.transfer(playerId, request);
    }

    @PostMapping("/{playerId}/claims")
    @Operation(summary = "Claim reward into wallet")
    public WalletDtos.TransactionResponse claimReward(@PathVariable @NotBlank String playerId,
                                                      @Valid @RequestBody WalletDtos.ClaimRewardRequest request) {
        return service.claimReward(playerId, request);
    }

    @PostMapping("/{playerId}/refunds")
    @Operation(summary = "Refund wallet transaction")
    public WalletDtos.TransactionResponse refund(@PathVariable @NotBlank String playerId,
                                                 @Valid @RequestBody WalletDtos.RefundRequest request) {
        return service.refund(playerId, request);
    }

    @GetMapping("/{playerId}/balance")
    @Operation(summary = "Get wallet balance")
    public WalletDtos.WalletResponse balance(@PathVariable @NotBlank String playerId) {
        return service.balance(playerId);
    }

    @GetMapping("/{playerId}/transactions")
    @Operation(summary = "Get wallet transaction history")
    public Page<WalletDtos.TransactionResponse> history(@PathVariable @NotBlank String playerId,
                                                        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                                        Pageable pageable) {
        return service.history(playerId, pageable);
    }
}
