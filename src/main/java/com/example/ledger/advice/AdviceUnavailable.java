package com.example.ledger.advice;

/** 모델 연결 상태와 입력 데이터 부족을 HTTP 응답에서 구분합니다. */
public class AdviceUnavailable extends RuntimeException {
    private final int status;
    private final String code;

    public AdviceUnavailable(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
