package com.example.ledger.common;

import java.util.*;
import com.example.ledger.advice.AdviceUnavailable;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, Object>> missing(NoSuchElementException e) {
        return ResponseEntity.status(404).body(Map.of("message", "내역을 찾을 수 없습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        var fields = new LinkedHashMap<String, String>();
        e.getBindingResult()
            .getFieldErrors()
            .forEach(f -> fields.putIfAbsent(
                f.getField().equals("expenseKindValid") ? "expenseKind" : f.getField(), f.getDefaultMessage()
            ));
        return ResponseEntity.badRequest().body(Map.of("message", "입력값을 확인해주세요.", "fields", fields));
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        java.time.DateTimeException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<Map<String, Object>> malformed(Exception e) {
        return ResponseEntity.badRequest().body(
            Map.of("message", "날짜, 금액, 필수 항목 또는 분류 값이 올바르지 않습니다.")
        );
    }
    @ExceptionHandler(AdviceUnavailable.class)
    ResponseEntity<Map<String, Object>> adviceUnavailable(AdviceUnavailable e) {
        return ResponseEntity.status(e.status()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }
}

// 처리 과정 예외 처리