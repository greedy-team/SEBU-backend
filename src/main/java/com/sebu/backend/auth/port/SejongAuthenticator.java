package com.sebu.backend.auth.port;

public interface SejongAuthenticator {
    SejongUserProfile authenticate(String studentId, String password);
}
