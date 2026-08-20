package com.law.annotation.auth.dto;

public record CsrfTokenResponse(String headerName, String token) {
}
