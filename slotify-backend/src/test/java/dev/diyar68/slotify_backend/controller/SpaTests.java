package dev.diyar68.slotify_backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.diyar68.slotify_backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpaController.class)
@Import(SecurityConfig.class)
class SpaTests {

    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(
        strings = {
            "/",
            "/login",
            "/dashboard/services",
            "/book/max-friseur",
            "/register/complete?token=test",
        }
    )
    void publicPagesForwardToReact(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void missingAssetsAndApiStay404() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/api/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/missing").with(user("demo"))).andExpect(status().isNotFound());
    }

    @Test
    void protectedApiStillRequiresLogin() throws Exception {
        mvc.perform(get("/api/services")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/services").with(user("demo").roles("PROVIDER"))).andExpect(
            status().isForbidden()
        );
    }
}
