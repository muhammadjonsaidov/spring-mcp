package io.salesdoctor.spring_mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SpringMcpApplicationTests {

	@Test
	void contextLoads() {
	}

}
