package com.wex.purchases;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PurchaseTrackerApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts cleanly
        // with the default H2 datasource and all wiring intact.
    }
}
