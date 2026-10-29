package com.cancerweb.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * The real CSRF cookie round trip, as the browser does it. It needs its own application context: Spring
 * Security's test {@code csrf()} helper, used by the other tests, swaps the shared context's token repository for
 * one that writes no cookie.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieFlowTest extends IntegrationTest {

    @Test
    void csrfEndpointIssuesTokenThatLoginAccepts() throws Exception {
        createUser("Flow", "flow@example.com", Role.PATIENT);
        Cookie xsrf = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();

        String credentials = "{\"email\":\"flow@example.com\",\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials)
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials)
                        .cookie(xsrf).header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden());
    }
}
