package com.financehub;

import com.financehub.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainer.class)
class FinanceHubApplicationTests {

    @Test
    void contextLoads() {
    }
}
