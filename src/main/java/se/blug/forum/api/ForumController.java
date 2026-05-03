package se.blug.forum.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.blug.forum.model.ForumData;
import se.blug.forum.repo.ForumRepository;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ForumController {

    private final ForumRepository forumRepository;

    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }

    @GetMapping("/post")
    public String post() {
        ForumData entity = new ForumData();
        entity.setContent("Hello, World! - A blog from angie");
        forumRepository.save(entity);
        return "OK";
    }

    @GetMapping("/list")
    public Iterable<ForumData> list() {
        return forumRepository.findAll();
    }
    @GetMapping("/delete")
    public void delete ()
    {
        forumRepository.deleteAll();
    }

}
