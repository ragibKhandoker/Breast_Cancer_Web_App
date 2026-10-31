package com.cancerweb.backend.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.security.AuthCookies;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * Per-tab sessions: each tab sends its own slot, gets its own login cookie, and logging out in one tab leaves
 * the others signed in.
 */
class TabSessionsTest extends IntegrationTest {

    private static final String ADMIN_TAB = "admintab";
    private static final String PATIENT_TAB = "patient1";

    private Cookie loginIn(String slot, String email) throws Exception {
        return mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                        .header(AuthCookies.SLOT_HEADER, slot))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("bc_auth_" + slot))
                .andExpect(cookie().doesNotExist("bc_auth"))
                .andReturn().getResponse().getCookie("bc_auth_" + slot);
    }

    @Test
    void twoTabsHoldTwoAccountsAndLoggingOutOneKeepsTheOther() throws Exception {
        createUser("Admin", "admin@example.com", Role.ADMIN);
        createUser("Rahima Begum", "rahima@example.com", Role.PATIENT);
        Cookie admin = loginIn(ADMIN_TAB, "admin@example.com");
        Cookie patient = loginIn(PATIENT_TAB, "rahima@example.com");

        // The browser sends both cookies with every request; the slot picks the tab's own.
        mvc.perform(get("/api/auth/me").cookie(admin, patient).header(AuthCookies.SLOT_HEADER, ADMIN_TAB))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/auth/me").cookie(admin, patient).header(AuthCookies.SLOT_HEADER, PATIENT_TAB))
                .andExpect(jsonPath("$.role").value("PATIENT"));
        // Plain links (an image) name the slot in the URL instead.
        mvc.perform(get("/api/auth/me").cookie(admin, patient).param(AuthCookies.SLOT_PARAM, PATIENT_TAB))
                .andExpect(jsonPath("$.role").value("PATIENT"));

        // Logging out in the admin tab clears only that tab's cookie and revokes only its token.
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(admin, patient).header(AuthCookies.SLOT_HEADER, ADMIN_TAB))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("bc_auth_" + ADMIN_TAB, 0));
        mvc.perform(get("/api/auth/me").cookie(admin).header(AuthCookies.SLOT_HEADER, ADMIN_TAB)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(admin, patient).header(AuthCookies.SLOT_HEADER, PATIENT_TAB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("rahima@example.com"));
    }

    @Test
    void aNewTabCopiesTheLoginIntoItsOwnSessionSoLoggingOutThereKeepsTheFirstTab() throws Exception {
        createUser("Admin", "admin@example.com", Role.ADMIN);
        createUser("Rahima Begum", "rahima@example.com", Role.PATIENT);
        Cookie first = loginIn(ADMIN_TAB, "admin@example.com");

        // Tab 2 opens: it copies tab 1's login into its own slot and cookie.
        Cookie second = mvc.perform(post("/api/auth/fork").with(csrf()).cookie(first)
                        .header(AuthCookies.SLOT_HEADER, "secondtb").header(AuthCookies.FROM_SLOT_HEADER, ADMIN_TAB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(cookie().exists("bc_auth_secondtb"))
                .andReturn().getResponse().getCookie("bc_auth_secondtb");
        org.assertj.core.api.Assertions.assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Logging out in tab 2 leaves tab 1 signed in as the administrator.
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(first, second).header(AuthCookies.SLOT_HEADER, "secondtb"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(first, second).header(AuthCookies.SLOT_HEADER, "secondtb")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(first, second).header(AuthCookies.SLOT_HEADER, ADMIN_TAB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));

        // Tab 2 now signs in as the patient, while tab 1 stays the administrator.
        Cookie patient = loginIn("secondtb", "rahima@example.com");
        mvc.perform(get("/api/auth/me").cookie(first, patient).header(AuthCookies.SLOT_HEADER, "secondtb"))
                .andExpect(jsonPath("$.role").value("PATIENT"));
        mvc.perform(get("/api/auth/me").cookie(first, patient).header(AuthCookies.SLOT_HEADER, ADMIN_TAB))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void thereIsNothingToCopyWithoutAValidLogin() throws Exception {
        createUser("Admin", "admin@example.com", Role.ADMIN);
        Cookie first = loginIn(ADMIN_TAB, "admin@example.com");
        // No source slot, or a logged-out one: 401 and no cookie.
        mvc.perform(post("/api/auth/fork").with(csrf()).header(AuthCookies.SLOT_HEADER, "secondtb"))
                .andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist("bc_auth_secondtb"));
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(first).header(AuthCookies.SLOT_HEADER, ADMIN_TAB));
        mvc.perform(post("/api/auth/fork").with(csrf()).cookie(first)
                        .header(AuthCookies.SLOT_HEADER, "secondtb").header(AuthCookies.FROM_SLOT_HEADER, ADMIN_TAB))
                .andExpect(status().isUnauthorized());
        // Without the CSRF token another site cannot make the browser copy a login.
        Cookie again = loginIn(ADMIN_TAB, "admin@example.com");
        mvc.perform(post("/api/auth/fork").cookie(again)
                        .header(AuthCookies.SLOT_HEADER, "secondtb").header(AuthCookies.FROM_SLOT_HEADER, ADMIN_TAB))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMalformedSlotFallsBackToTheDefaultCookie() throws Exception {
        createUser("Rahima Begum", "rahima@example.com", Role.PATIENT);
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"rahima@example.com\",\"password\":\"" + PASSWORD + "\"}")
                        .header(AuthCookies.SLOT_HEADER, "../../evil"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("bc_auth"));
        // Without a slot (older pages) the default cookie still works.
        Cookie plain = login("rahima@example.com", PASSWORD);
        mvc.perform(get("/api/auth/me").cookie(plain)).andExpect(status().isOk());
        // Another tab's slot does not see the default login.
        mvc.perform(get("/api/auth/me").cookie(plain).header(AuthCookies.SLOT_HEADER, PATIENT_TAB)).andExpect(status().isUnauthorized());
    }
}
