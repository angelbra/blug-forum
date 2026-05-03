package se.blug.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class UserData {
    @Id
    private Long id;
    private String username;
    private String password;
    private String email;
    private String role;

}
