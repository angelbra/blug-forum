package se.blug.forum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import se.blug.forum.model.*;
import se.blug.forum.repo.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApiSecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ForumRepository forums;
    @Autowired ThreadRepository threads;
    @Autowired PostRepository posts;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    User owner, other, admin;
    Forum forum;
    ForumThread privateThread, publicThread;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String suffix = java.util.UUID.randomUUID().toString();
        owner = account("owner" + suffix, User.Role.USER);
        other = account("other" + suffix, User.Role.USER);
        admin = account("admin" + suffix, User.Role.ADMIN);
        forum = new Forum(); forum.setName("Synthetic forum"); forum.setCreatedBy(owner); forums.saveAndFlush(forum);
        privateThread = makeThread("Private metadata", false);
        publicThread = makeThread("Public", true);
    }
    User account(String username, User.Role role) {
        User u = new User(); u.setUsername(username); u.setEmail(username + "@example.test");
        u.setPassword(encoder.encode("test-password")); u.setRole(role); return users.saveAndFlush(u);
    }
    ForumThread makeThread(String title, boolean visible) {
        ForumThread t = new ForumThread(); t.setTitle(title); t.setContent("Secret thread body");
        t.setForum(forum); t.setOwner(owner); t.setPublic(visible); return threads.saveAndFlush(t);
    }
    Post makePost(ForumThread thread, User author) {
        Post p = new Post(); p.setContent("Synthetic post"); p.setForumThread(thread); p.setAuthor(author); return posts.saveAndFlush(p);
    }
    MockHttpSession login(User u) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
            .param("username", u.getUsername()).param("password", "test-password"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    @Test void publicReadsAndProtectedWrites() throws Exception {
        mvc.perform(get("/api/forums")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
        mvc.perform(get("/api/threads/" + publicThread.getId())).andExpect(status().isOk());
        mvc.perform(post("/api/forums").with(csrf()).contentType("application/json").content("{\"name\":\"Forum\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void realLoginAndAuthenticatedWrite() throws Exception {
        MockHttpSession session = login(owner);
        mvc.perform(post("/api/forums").session(session).with(csrf()).contentType("application/json")
            .content("{\"name\":\"Created through session\",\"createdBy\":{\"id\":" + other.getId() + "},\"blocked\":true}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("blocked").value(false));
        assertEquals(owner.getId(), forums.findAll().stream().filter(f -> f.getName().equals("Created through session")).findFirst().orElseThrow().getCreatedBy().getId());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", owner.getUsername()).param("password", "wrong"))
            .andExpect(status().isUnauthorized());
    }
    @Test void registrationCannotEscalateAndRejectsInvalidData() throws Exception {
        String name = "registered" + java.util.UUID.randomUUID();
        String body = "{\"username\":\"" + name + "\",\"email\":\"" + name + "@example.test\",\"password\":\"test-password\",\"role\":\"ADMIN\",\"blocked\":true}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated());
        User registered = users.findByUsername(name).orElseThrow();
        assertEquals(User.Role.USER, registered.getRole()); assertFalse(registered.isBlocked()); assertTrue(encoder.matches("test-password", registered.getPassword()));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
    @Test void privateContentAndRelationshipsDoNotLeak() throws Exception {
        Post p = makePost(privateThread, owner);
        mvc.perform(get("/api/forums/" + forum.getId() + "/threads")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secret thread body"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ownerId"))));
        mvc.perform(get("/api/threads/" + privateThread.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/threads/" + privateThread.getId() + "/posts")).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + p.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/threads/" + privateThread.getId()).session(login(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/threads/" + privateThread.getId()).session(login(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("password").doesNotExist()).andExpect(jsonPath("owner").doesNotExist());
        privateThread.getParticipants().add(other); threads.flush();
        mvc.perform(get("/api/threads/" + privateThread.getId()).session(login(other))).andExpect(status().isOk());
    }
    @Test void ownPostsOnlyAndDeletedPostsStayHidden() throws Exception {
        Post p = makePost(publicThread, owner); MockHttpSession session = login(owner);
        mvc.perform(put("/api/posts/" + p.getId()).session(login(other)).with(csrf()).contentType("application/json").content("{\"content\":\"No\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/posts/" + p.getId()).session(login(other)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/posts/" + p.getId()).session(session).with(csrf()).contentType("application/json").content("{\"content\":\"Edited\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("content").value("Edited"));
        mvc.perform(delete("/api/posts/" + p.getId()).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + p.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/threads/" + publicThread.getId() + "/posts")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void csrfIsRequiredIncludingLoginAndRegistration() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("headerName").value("X-CSRF-TOKEN"));
        mvc.perform(post("/api/auth/login").param("username", owner.getUsername()).param("password", "test-password")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        MockHttpSession session = login(owner);
        mvc.perform(post("/api/forums").session(session).contentType("application/json").content("{\"name\":\"Forum\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/forums").session(session).with(csrf().useInvalidToken()).contentType("application/json").content("{\"name\":\"Forum\"}")).andExpect(status().isForbidden());
    }
    @Test void ownedThreadEditingDeletionAndCurrentOwnership() throws Exception {
        MockHttpSession ownerSession = login(owner), otherSession = login(other);
        String path = "/api/threads/" + publicThread.getId();
        mvc.perform(put(path).session(otherSession).with(csrf()).contentType("application/json").content("{\"title\":\"No\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete(path).session(otherSession).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put(path).session(ownerSession).with(csrf()).contentType("application/json").content("{\"title\":\"Edited\",\"content\":\"Updated body\",\"owner\":{\"id\":" + other.getId() + "}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("ownerId").value(owner.getId()));
        mvc.perform(post(path + "/transfer-ownership").session(ownerSession).with(csrf()).contentType("application/json").content("{\"id\":" + other.getId() + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("ownerId").value(other.getId()));
        mvc.perform(put(path + "/visibility").session(ownerSession).with(csrf()).contentType("application/json").content("{\"isPublic\":false}"))
            .andExpect(status().isForbidden());
        mvc.perform(put(path + "/visibility").session(otherSession).with(csrf()).contentType("application/json").content("{\"isPublic\":false}"))
            .andExpect(status().isOk());
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(get(path).session(ownerSession)).andExpect(status().isOk());
        makePost(publicThread, owner);
        mvc.perform(delete(path).session(otherSession).with(csrf())).andExpect(status().isNoContent());
        assertFalse(threads.existsById(publicThread.getId()));
        assertTrue(posts.findByForumThreadId(publicThread.getId()).isEmpty());
    }
    @Test void ownershipTransferRequiresExistingUnblockedUser() throws Exception {
        MockHttpSession session = login(owner);
        String path = "/api/threads/" + privateThread.getId() + "/transfer-ownership";
        mvc.perform(post(path).session(session).with(csrf()).contentType("application/json").content("{\"id\":999999999}"))
            .andExpect(status().isNotFound());
        other.setBlocked(true); users.flush();
        mvc.perform(post(path).session(session).with(csrf()).contentType("application/json").content("{\"id\":" + other.getId() + "}"))
            .andExpect(status().isForbidden());
    }
    @Test void profilePrivacyValidationAndRoleEscalation() throws Exception {
        MockHttpSession session = login(owner);
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("email").value(owner.getEmail()))
            .andExpect(jsonPath("password").doesNotExist());
        mvc.perform(get("/api/users/" + other.getId()).session(session)).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/me").session(session).with(csrf()).contentType("application/json").content("{\"email\":\"bad\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/users/me").session(session).with(csrf()).contentType("application/json").content("{\"email\":\"" + other.getEmail() + "\"}"))
            .andExpect(status().isConflict());
        String email = "updated" + java.util.UUID.randomUUID() + "@example.test";
        mvc.perform(put("/api/users/me").session(session).with(csrf()).contentType("application/json")
            .content("{\"email\":\"" + email + "\",\"role\":\"ADMIN\",\"id\":" + other.getId() + ",\"blocked\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("role").value("USER")).andExpect(jsonPath("id").value(owner.getId()))
            .andExpect(jsonPath("blocked").value(false));
        mvc.perform(put("/api/users/me").session(session).with(csrf()).contentType("application/json").content("{\"password\":\"changed-password\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("password").doesNotExist());
        assertTrue(session.isInvalid());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", owner.getUsername()).param("password", "changed-password"))
            .andExpect(status().isOk());
    }
    @Test void deleteProfileSafelyCleansRelationshipsAndOldSessions() throws Exception {
        ForumThread otherThread = makeThread("Another owner", false); otherThread.setOwner(other);
        otherThread.getModerators().add(owner); otherThread.getParticipants().add(owner); threads.flush();
        makePost(otherThread, owner); makePost(privateThread, other);
        MockHttpSession first = login(owner), second = login(owner);
        mvc.perform(delete("/api/users/me").session(first).with(csrf())).andExpect(status().isNoContent());
        assertTrue(first.isInvalid()); assertFalse(users.existsById(owner.getId()));
        assertFalse(threads.existsById(privateThread.getId())); assertTrue(posts.findByAuthorId(owner.getId()).isEmpty());
        assertTrue(otherThread.getModerators().isEmpty()); assertTrue(otherThread.getParticipants().isEmpty()); assertNull(forum.getCreatedBy());
        account(owner.getUsername(), User.Role.USER);
        mvc.perform(get("/api/users/me").session(second)).andExpect(status().isUnauthorized());
    }
    @Test void blockedExistingSessionAndAdminAuthorization() throws Exception {
        MockHttpSession memberSession = login(other), adminSession = login(admin);
        mvc.perform(post("/api/admin/users/" + owner.getId() + "/block").session(memberSession).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/" + other.getId() + "/block").session(adminSession).with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("password").doesNotExist());
        mvc.perform(post("/api/forums").session(memberSession).with(csrf()).contentType("application/json").content("{\"name\":\"Blocked write\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", other.getUsername()).param("password", "test-password")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/users/" + other.getId() + "/restore").session(adminSession).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/forums").session(memberSession).with(csrf()).contentType("application/json").content("{\"name\":\"Restored write\"}"))
            .andExpect(status().isCreated());
        admin.setBlocked(true); users.flush();
        mvc.perform(post("/api/admin/users/" + owner.getId() + "/block").session(adminSession).with(csrf())).andExpect(status().isForbidden());
    }
}
