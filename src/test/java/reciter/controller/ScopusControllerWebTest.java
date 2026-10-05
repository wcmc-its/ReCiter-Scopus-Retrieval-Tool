package reciter.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import reciter.Application;

/**
 * Verifies that malformed requests are answered with HTTP 400 (via
 * {@link GlobalExceptionHandler}) rather than a 500. The 400 paths fail in {@code validate}
 * before any Scopus call is made, so no credentials or network are needed.
 *
 * <p>Uses an explicit {@code classes = Application.class} because the app bootstraps with
 * {@code @EnableAutoConfiguration} + {@code @ComponentScan} rather than
 * {@code @SpringBootApplication}, so Spring's default config search finds nothing.</p>
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ScopusControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unsupportedTypeReturns400() throws Exception {
        mockMvc.perform(post("/scopus/query/")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":[\"123\"],\"type\":\"eid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyQueryReturns400() throws Exception {
        mockMvc.perform(post("/scopus/query/")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":[],\"type\":\"pmid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/scopus/query/")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
