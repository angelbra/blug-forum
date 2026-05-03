package se.blug.forum.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import se.blug.forum.model.ForumData;

public interface ForumRepository extends JpaRepository<ForumData, Long> {
}
