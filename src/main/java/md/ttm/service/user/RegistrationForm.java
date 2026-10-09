package md.ttm.service.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datele minime pentru crearea unui cont din pagina publică de înregistrare.
 */
public class RegistrationForm {

    @NotBlank(message = "Introduceți prenumele")
    @Size(max = 100, message = "Maximum 100 de caractere")
    private String firstName = "";

    @NotBlank(message = "Introduceți numele")
    @Size(max = 100, message = "Maximum 100 de caractere")
    private String lastName = "";

    @NotBlank(message = "Introduceți numele de utilizator")
    @Size(min = 3, max = 50, message = "Între 3 și 50 de caractere")
    @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Doar litere latine, cifre și . _ -")
    private String username = "";

    @NotBlank(message = "Introduceți parola")
    @Size(min = UserService.MIN_PASSWORD_LENGTH, max = 100,
            message = "Parola trebuie să aibă cel puțin " + UserService.MIN_PASSWORD_LENGTH + " caractere")
    private String password = "";

    public RegistrationForm() {
    }

    public RegistrationForm(String firstName, String lastName, String username, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
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
}
