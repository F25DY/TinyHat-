package com.hexora.hexora;

import com.hexora.test.IsolatedMySqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "hexora.network.discovery-enabled=false")
class HexoraApplicationTests extends IsolatedMySqlTestDatabase {

	@Test
	void contextLoads() {
	}

}
