package com.example.walletledger.api;

import com.example.walletledger.exception.ApiExceptions.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    record ErrorResponse(Instant timestamp, int status, String error, String message) { }

    @ExceptionHandler(WalletNotFound.class)
    ResponseEntity<ErrorResponse> notFound(WalletNotFound e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InsufficientFunds.class)
    ResponseEntity<ErrorResponse> conflict(InsufficientFunds e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflict.class)
    ResponseEntity<ErrorResponse> idempotencyConflict(IdempotencyConflict e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler({RefundNotFound.class, InvalidRefund.class, TransferNotFound.class, InvalidTransfer.class, ClaimRewardConflict.class, InvalidClaimReward.class})
    ResponseEntity<ErrorResponse> refundErrors(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception e) {
        var message = e instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().map(x -> x.getField() + ": " + x.getDefaultMessage()).collect(Collectors.joining(", "))
                : e.getMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
