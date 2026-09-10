package com.sebu.backend.support;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Valid cookie/header fixtures for business API tests; dedicated security tests use real /csrf cookies. */
public final class CookieApiRequests {
    public static final String ORIGIN = "https://sebu-frontend.vercel.app";
    private static final String CSRF = "3661f998-014e-4a20-b7cb-43b4638a1749";

    private CookieApiRequests() { }

    public static MockHttpServletRequestBuilder post(String path, Object... variables) {
        return protect(MockMvcRequestBuilders.post(path, variables));
    }

    public static MockHttpServletRequestBuilder put(String path, Object... variables) {
        return protect(MockMvcRequestBuilders.put(path, variables));
    }

    public static MockHttpServletRequestBuilder patch(String path, Object... variables) {
        return protect(MockMvcRequestBuilders.patch(path, variables));
    }

    public static MockHttpServletRequestBuilder delete(String path, Object... variables) {
        return protect(MockMvcRequestBuilders.delete(path, variables));
    }

    private static MockHttpServletRequestBuilder protect(MockHttpServletRequestBuilder request) {
        return request.header("Origin", ORIGIN).header("X-XSRF-TOKEN", CSRF)
            .cookie(new Cookie("XSRF-TOKEN", CSRF));
    }
}
