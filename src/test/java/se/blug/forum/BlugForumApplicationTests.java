package se.blug.forum;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/blug_forum_test")
@ActiveProfiles("test")
class BlugForumApplicationTests {

	@Test
	void contextLoads() {
	}

}
