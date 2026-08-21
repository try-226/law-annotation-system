package com.law.annotation.auth;

public final class AuthErrorCodes {

    public static final String INVALID_CREDENTIALS = "AUTH.INVALID_CREDENTIALS";
    public static final String UNAUTHENTICATED = "AUTH.UNAUTHENTICATED";
    public static final String FORBIDDEN = "AUTH.FORBIDDEN";
    public static final String CSRF_INVALID = "AUTH.CSRF_INVALID";
    public static final String OLD_PASSWORD_INCORRECT = "AUTH.OLD_PASSWORD_INCORRECT";

    private AuthErrorCodes() {
    }
}
