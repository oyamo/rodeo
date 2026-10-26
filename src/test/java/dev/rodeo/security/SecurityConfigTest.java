package dev.rodeo.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "github.webhook-secret=test-secret",
        "gitlab.webhook-secret=test-token"
})
@AutoConfigureMockMvc
class SecurityConfigTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownPathsAreDenied() throws Exception {
        mockMvc.perform(get("/anything-else"))
                .andExpect(status().isForbidden());
    }

    @Test
    void githubPathIsReachableWithoutCsrfTokenButStillRequiresSignature() throws Exception {
        mockMvc.perform(post("/webhooks/github").header("X-GitHub-Event", "push").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gitlabPathIsReachableWithoutCsrfTokenButStillRequiresToken() throws Exception {
        mockMvc.perform(post("/webhooks/gitlab").header("X-Gitlab-Event", "Push Hook").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
