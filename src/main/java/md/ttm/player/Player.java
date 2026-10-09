package md.ttm.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * Un jucător de tenis de masă.
 */
@Entity
@Table(name = "player")
public class Player {

    public static final int DEFAULT_RATING = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @NotBlank(message = "Introduceți prenumele")
    @Size(max = 100, message = "Maximum 100 de caractere")
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Introduceți numele")
    @Size(max = 100, message = "Maximum 100 de caractere")
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @NotNull(message = "Alegeți stilul de joc")
    @Enumerated(EnumType.STRING)
    @Column(name = "play_style", nullable = false, length = 20)
    private PlayStyle playStyle = PlayStyle.ATTACK;

    @Size(max = 100, message = "Maximum 100 de caractere")
    @Column(name = "city", length = 100)
    private String city;

    @Size(max = 30, message = "Maximum 30 de caractere")
    @Pattern(regexp = "^[+0-9 ()-]*$", message = "Doar cifre, spații și caracterele + ( ) -")
    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "play_hand", length = 10)
    private PlayHand playHand;

    @Size(max = 100, message = "Maximum 100 de caractere")
    @Column(name = "blade", length = 100)
    private String blade;

    @Size(max = 100, message = "Maximum 100 de caractere")
    @Column(name = "forehand_rubber", length = 100)
    private String forehandRubber;

    @Size(max = 100, message = "Maximum 100 de caractere")
    @Column(name = "backhand_rubber", length = 100)
    private String backhandRubber;

    @Min(value = 0, message = "Ratingul nu poate fi negativ")
    @Max(value = 5000, message = "Ratingul maxim este 5000")
    @Column(name = "rating", nullable = false)
    private int rating = DEFAULT_RATING;

    @Min(value = 0, message = "Valoare negativă")
    @Column(name = "wins", nullable = false)
    private int wins;

    @Min(value = 0, message = "Valoare negativă")
    @Column(name = "losses", nullable = false)
    private int losses;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Player() {
    }

    public Player(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /** Numele afișat în liste: "Nume Prenume". */
    public String getDisplayName() {
        return lastName + " " + firstName;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
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

    public PlayStyle getPlayStyle() {
        return playStyle;
    }

    public void setPlayStyle(PlayStyle playStyle) {
        this.playStyle = playStyle;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public PlayHand getPlayHand() {
        return playHand;
    }

    public void setPlayHand(PlayHand playHand) {
        this.playHand = playHand;
    }

    public String getBlade() {
        return blade;
    }

    public void setBlade(String blade) {
        this.blade = blade;
    }

    public String getForehandRubber() {
        return forehandRubber;
    }

    public void setForehandRubber(String forehandRubber) {
        this.forehandRubber = forehandRubber;
    }

    public String getBackhandRubber() {
        return backhandRubber;
    }

    public void setBackhandRubber(String backhandRubber) {
        this.backhandRubber = backhandRubber;
    }

    /** Are completat cel puțin un element de echipament. */
    public boolean hasEquipment() {
        return blade != null || forehandRubber != null || backhandRubber != null;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Player other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Player[" + id + ", " + getDisplayName() + "]";
    }
}
