package se.blug.forum.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.blug.forum.model.ForumData;
import java.util.List;

@Repository
public interface ForumRepository extends JpaRepository<ForumData, Long> {
    List<ForumData> findAllByOrderByCreatedAtDesc();
}
