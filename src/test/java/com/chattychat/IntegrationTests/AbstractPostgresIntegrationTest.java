package com.chattychat.IntegrationTests;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need the full application context, and therefore a real database.
 * <p>
 * Everything these tests need is declared here rather than taken from the ambient environment:
 * <ul>
 *   <li><b>The database</b> comes from a Testcontainers Postgres wired in by {@code @ServiceConnection},
 *       not from {@code TEST_DB_URL} pointing at a hand-started local container. The suite therefore needs
 *       Docker, but no environment variables, no {@code .env}, and no {@code -Dspring.profiles.active=test}.</li>
 *   <li><b>The schema</b> comes from {@code schema.sql}, which only runs because
 *       {@code spring.sql.init.mode=always} is forced below. Postgres is not an embedded database, so the
 *       default ({@code embedded}) skips the script entirely and every query fails with
 *       {@code relation "users" does not exist}. {@code ddl-auto} also defaults to {@code none} for a
 *       non-embedded datasource, so Hibernate will not create the tables either.</li>
 *   <li><b>The OAuth2 client registrations</b> are stubbed. {@code application.properties} resolves them from
 *       {@code ${GOOGLE_CLIENT_ID}} and friends; without these overrides the context fails to start on any
 *       machine that has no {@code .env}, which is every CI runner.</li>
 * </ul>
 * The container follows the Testcontainers singleton pattern — started once in a static initialiser and
 * shared by every subclass for the life of the JVM, with Ryuk responsible for tearing it down. It is
 * deliberately not a JUnit {@code @Container}: that lifecycle stops the container when the first test class
 * finishes, leaving the next class to connect to a dead database.
 */
@SpringBootTest(properties = {
        // schema.sql is the source of truth for the test schema; see class javadoc for why this is required.
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=none",

        // Stubs: these are never used to talk to a provider, they only have to be non-null so the
        // ClientRegistrationRepository can be built.
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "spring.security.oauth2.client.registration.github.client-id=test-github-client-id",
        "spring.security.oauth2.client.registration.github.client-secret=test-github-client-secret"
})
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        // PostgreSQLContainer's own wait strategy already blocks until the server accepts connections.
        POSTGRES.start();
    }
}