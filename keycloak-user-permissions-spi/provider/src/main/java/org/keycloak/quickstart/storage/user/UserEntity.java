package org.keycloak.quickstart.storage.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

@NamedQueries({
    @NamedQuery(name = "getUserByUsername", query = "select u from UserEntity u where u.username = :username"),
    @NamedQuery(name = "getUserByEmail", query = "select u from UserEntity u where u.email = :email"),
    @NamedQuery(name = "getUserCount", query = "select count(u) from UserEntity u"),
    @NamedQuery(name = "getAllUsers", query = "select u from UserEntity u"),
    @NamedQuery(name = "searchForUser", query = "select u from UserEntity u where "
            + "( lower(u.username) like :search or u.email like :search ) order by u.username"),
})
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private String id;
    private String username;
    private String email;
    private String password;
    private String phone;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
