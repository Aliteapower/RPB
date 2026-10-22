package com.rpb.reservation.dailyledger.api;

import com.rpb.reservation.dailyledger.domain.LedgerException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=LedgerController.class)
public class LedgerErrorHandler {
    public record ErrorDetail(String code,String message) {}
    public record ErrorResponse(ErrorDetail error) {}
    @ExceptionHandler(LedgerException.class)
    public ResponseEntity<ErrorResponse> handle(LedgerException error){return ResponseEntity.status(error.status()).body(new ErrorResponse(new ErrorDetail(error.code(),error.getMessage())));}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException error){return ResponseEntity.badRequest().body(new ErrorResponse(new ErrorDetail("REQUEST_INVALID","请求字段无效")));}
}
