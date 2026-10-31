package com.cancerweb.backend;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.cancerweb.backend.exception.ChatUnavailableException;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.service.BreastHealthAssistant;
import com.cancerweb.backend.service.ChatService;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** POST /api/chat with Claude replaced by a mock: no API key or network needed. */
class ChatApiTest extends IntegrationTest {

    private static final String QUESTION = "{\"messages\":[{\"role\":\"user\",\"content\":\"What are early signs of breast cancer?\"}]}";

    @MockitoBean
    private BreastHealthAssistant assistant;

    @Test
    void signedInUsersGetAnswersAndOffTopicQuestionsAreDeclined() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "chat@example.com");
        when(assistant.ask(anyList())).thenReturn(new BreastHealthAssistant.Answer(true, "A new lump, skin dimpling..."));
        mvc.perform(withJson(post("/api/chat"), QUESTION).cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("A new lump, skin dimpling..."))
                .andExpect(jsonPath("$.onTopic").value(true));

        when(assistant.ask(anyList())).thenReturn(new BreastHealthAssistant.Answer(false, ""));
        mvc.perform(withJson(post("/api/chat"),
                        "{\"messages\":[{\"role\":\"user\",\"content\":\"Who won the football match?\"}]}").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(ChatService.OFF_TOPIC_REPLY))
                .andExpect(jsonPath("$.onTopic").value(false));

        Cookie doctor = signedIn(Role.DOCTOR, "chat.doctor@example.com");
        mvc.perform(withJson(post("/api/chat"), QUESTION).cookie(doctor)).andExpect(status().isOk());
    }

    @Test
    void signedOutVisitorsAreRefused() throws Exception {
        // (CSRF itself is checked end to end in CsrfCookieFlowTest; the csrf() test helper changes it here.)
        mvc.perform(withJson(post("/api/chat"), QUESTION)).andExpect(status().isUnauthorized());
        verify(assistant, never()).ask(anyList());
    }

    @Test
    void badConversationsAre400AndAnUnavailableChatbotIs503() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "chat@example.com");
        mvc.perform(withJson(post("/api/chat"), "{\"messages\":[]}").cookie(patient))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Type a question first."));
        mvc.perform(withJson(post("/api/chat"), "null").cookie(patient)).andExpect(status().isBadRequest());
        verify(assistant, never()).ask(anyList());

        when(assistant.ask(anyList())).thenThrow(new ChatUnavailableException("The chatbot is not set up on this server yet.", null));
        mvc.perform(withJson(post("/api/chat"), QUESTION).cookie(patient))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The chatbot is not set up on this server yet."));
    }
}
