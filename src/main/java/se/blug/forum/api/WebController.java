package se.blug.forum.api;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.blug.forum.model.ForumData;
import se.blug.forum.repo.ForumRepository;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/")
public class WebController {

    private final ForumRepository forumRepository;

    @GetMapping("/")
    public String viewHomePage() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("posts", forumRepository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("newPost", new ForumData());
        return "dashboard";
    }

    @PostMapping("/posts")
    public String createBlogPost(@RequestParam String title, @RequestParam String content, RedirectAttributes redirectAttributes) {
        ForumData post = new ForumData();
        post.setTitle(title);
        post.setContent(content);
        forumRepository.save(post);
        redirectAttributes.addFlashAttribute("success", "Post created successfully!");
        return "redirect:/dashboard";
    }

    @PostMapping("/posts/welcome-linus")
    public String createWelcomePostForLinus(RedirectAttributes redirectAttributes) {
        ForumData post = new ForumData();
        post.setTitle("Välkomstmeddelande");
        post.setContent("Hej Linus, välkommen till min forum MVH Angelica");
        forumRepository.save(post);
        redirectAttributes.addFlashAttribute("success", "Välkomstmeddelande skapat!");
        return "redirect:/dashboard";
    }
}
