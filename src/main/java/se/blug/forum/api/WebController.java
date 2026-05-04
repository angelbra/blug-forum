package se.blug.forum.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("posts", forumRepository.findAll());
        return "dashboard";
    }

    @PostMapping("/posts")
    public String createBlogPost(
            @RequestParam String title,
            @RequestParam String content
    ) {
        System.out.println("New blog post title: " + title);
        System.out.println("New blog post content: " + content);
        ForumData entity = new ForumData();
        entity.setTitle(title);
        entity.setContent(content);
        forumRepository.save(entity);
        return "redirect:/dashboard";
    }

}
