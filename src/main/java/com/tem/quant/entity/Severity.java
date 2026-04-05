package com.tem.quant.entity;

/**
 * Web3 리스크 심각도 정의
 * 빌더 포인트: 단순 문자열보다 Enum을 사용해야 로직에서 실수를 방지할 수 있습니다.
 */
public enum Severity {
    CRITICAL, 
    HIGH, 
    MEDIUM, 
    LOW
}