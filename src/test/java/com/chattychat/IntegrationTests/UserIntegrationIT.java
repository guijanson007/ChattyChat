package com.chattychat.IntegrationTests;

import com.chattychat.Entities.User;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.AuthUser;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers

public class UserIntegrationIT {

    private static final Integer TIMEOUT = 120;
    private static final Logger logger = LoggerFactory.getLogger(UserIntegrationIT.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @BeforeAll
    static void startContainers() {
        Awaitility.await().atMost(Duration.ofSeconds(TIMEOUT)).until(postgres::isRunning);
        logger.info("PostgreSQL is up and running!");
    }


    @Test
    void shouldReturnUserById() throws Exception {
        // Pass null id so @GeneratedValue assigns it.
        User saved = userRepository.save(new User(
                null,
                "dummy_provider_id",
                "google",
                "dummy_name",
                "dummy_last_name",
                "dummy@gmail.com",
                "dummy"
        ));
        UUID id = saved.getId();

        mockMvc.perform(get("/v1/users/{userId}", id)
                        .with(user("test").roles("USER")))   // GET only needs auth, not CSRF
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.firstName").value("dummy_name"))
                .andExpect(jsonPath("$.displayName").value("dummy"));
    }

    @Test
    void shouldRejectDisplayNameUpdateForOtherUser() throws Exception {
        User target = userRepository.save(new User(
                null, "target_provider_id", "google",
                "target_first", "target_last", "target@gmail.com", "target_display"));

        AuthUser attacker = new AuthUser(
                UUID.randomUUID(),          // a DIFFERENT userId than target.getId()
                "google", "attacker_provider_id", "attacker",
                Map.of("sub", "attacker"));

        mockMvc.perform(patch("/v1/users/{userId}", target.getId())
                        .with(csrf())
                        .with(oauth2Login().oauth2User(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"hacked\"}"))
                .andExpect(status().isForbidden());

        // and the DB was not mutated
        assertThat(userRepository.findById(target.getId()).orElseThrow().getDisplayName())
                .isEqualTo("target_display");
    }

    @Test
    void shouldUpdateOwnDisplayName() throws Exception {
        User self = userRepository.save(new User(
                null, "self_provider_id", "google",
                "self_first", "self_last", "self@gmail.com", "old_display"));

        AuthUser me = new AuthUser(
                self.getId(),               // SAME id as the row being updated
                "google", "self_provider_id", "self",
                Map.of("sub", "self"));

        mockMvc.perform(patch("/v1/users/{userId}", self.getId())
                        .with(csrf())
                        .with(oauth2Login().oauth2User(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"new_display\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("new_display"));

        assertThat(userRepository.findById(self.getId()).orElseThrow().getDisplayName())
                .isEqualTo("new_display");
    }

}
