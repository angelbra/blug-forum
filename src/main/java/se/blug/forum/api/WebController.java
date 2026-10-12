package se.blug.forum.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.blug.forum.model.Forum;
import se.blug.forum.service.CurrentUser;
import se.blug.forum.service.ForumService;

@Controller
@RequiredArgsConstructor
public class WebController {
    private final ForumService service;
    private final CurrentUser currentUser;
    @GetMapping("/")
    public String home() { return "redirect:/dashboard"; }
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        currentUser.require(auth);
        model.addAttribute("posts", service.forums()); model.addAttribute("newPost", new Forum()); return "dashboard";
    }
    @PostMapping("/posts")
    public String create(@RequestParam String title, @RequestParam String content, Authentication auth, RedirectAttributes redirect) {
        Forum forum = new Forum(); forum.setName(title); forum.setDescription(title); forum.setContent(content);
        service.createForum(forum, currentUser.require(auth));
        redirect.addFlashAttribute("success", "Post created successfully!"); return "redirect:/dashboard";
    }
    @PostMapping("/posts/welcome-linus")
    public String welcome(Authentication auth, RedirectAttributes redirect) {
        return create("Välkomstmeddelande", "Hej Linus, välkommen till mitt forum. MVH Angelica", auth, redirect);
    }
}
