package com.chattychat;

import com.chattychat.IntegrationTests.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: the full application context starts.
 * <p>
 * The database, schema, and OAuth2 client stubs come from {@link AbstractPostgresIntegrationTest} — this test
 * needs <em>a</em> working datasource and does not care where it comes from.
 */
class ChattyChatApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
    }

}
