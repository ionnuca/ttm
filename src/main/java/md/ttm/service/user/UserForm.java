package md.ttm.service.user;

import md.ttm.model.user.AppUser;
import md.ttm.model.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datele din formularul de creare/editare a unui utilizator (pagina de administrare).
 * Parola e opțională la editare: dacă rămâne goală, se păstrează cea existentă.
 */
public class UserForm {

    @NotBlank(message = "Introduceți numele de utilizator")
    @Size(min = 3, max = 50, message = "Între 3 și 50 de caractere")
    @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Doar litere latine, cifre și . _ -")
    private String username = "";

    private String password = "";

    @Size(max = 100, message = "Maximum 100 de caractere")
    private String firstName = "";

    @Size(max = 100, message = "Maximum 100 de caractere")
    private String lastName = "";

    @NotNull(message = "Alegeți rolul")
    private Role role = Role.USER;

    private boolean enabled = true;

    public static UserForm from(AppUser user) {
        UserForm form = new UserForm();
        form.setUsername(user.getUsername());
        form.setRole(user.getRole());
        form.setEnabled(user.isEnabled());
        if (user.getPlayer() != null) {
            form.setFirstName(user.getPlayer().getFirstName());
            form.setLastName(user.getPlayer().getLastName());
        }
        return form;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
