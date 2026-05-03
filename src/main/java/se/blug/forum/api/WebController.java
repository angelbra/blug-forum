package se.blug.forum.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import se.blug.forum.model.ForumData;
import se.blug.forum.repo.ForumRepository;

@Controller
@RequiredArgsConstructor
public class WebController {
    private final ForumRepository forumRepository;

    @GetMapping("/")
    public String viewHomePage()
    {
        return "index";
    }

    @PostMapping("/posts")
    public String createBlogPost(
            @RequestParam String title,
            @RequestParam String content
    ) {
        // TODO: Save blog post in database later
        System.out.println("New blog post title: " + title);
        System.out.println("New blog post content: " + content);
        ForumData entity = new ForumData();
        entity.setContent(content);
        forumRepository.save(entity);
        return "redirect:/";
    }

}
