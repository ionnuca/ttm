package md.ttm.service.admin;

import md.ttm.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Pornirea și oprirea aplicației de test din aplicația de producție, fără acces la Docker: aplicația scrie doar
 * starea dorită („on”/„off”) în {@code test-desired}, într-un folder comun cu serverul. Pe server, serviciul
 * systemd instalat de {@code deploy/test-control-setup.sh} aplică starea și scrie starea reală în
 * {@code test-status} („running”, „starting” sau „stopped”, cu ora verificării).
 */
@Service
public class TestEnvironmentService {

    public enum State { RUNNING, STARTING, STOPPED, UNKNOWN }

    /** Starea reală, după ultima verificare a serverului ({@code checkedAt} = {@code null} dacă n-a verificat încă). */
    public record Status(State state, Instant checkedAt) {
    }

    static final String DESIRED = "test-desired";
    static final String STATUS = "test-status";

    private final Path dir;
    private final String url;
    private final boolean testApp;

    public TestEnvironmentService(@Value("${app.test-control.dir:}") String dir,
                                  @Value("${app.test-control.url:}") String url,
                                  @Value("${app.environment-label:}") String environmentLabel) {
        this.dir = dir == null || dir.isBlank() ? null : Path.of(dir);
        this.url = url == null ? "" : url.trim();
        this.testApp = environmentLabel != null && !environmentLabel.isBlank();
    }

    /** Comanda există doar în producție, când folderul comun e montat. */
    public boolean available() {
        return dir != null && !testApp && Files.isDirectory(dir);
    }

    /** Serviciul de pe server a răspuns măcar o dată (altfel: încă neinstalat). */
    public boolean serverServiceInstalled() {
        return available() && Files.exists(dir.resolve(STATUS));
    }

    public String url() {
        return url;
    }

    public boolean desiredOn() {
        if (!available()) {
            return false;
        }
        try {
            return "on".equals(Files.readString(dir.resolve(DESIRED), StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            return false;
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void setDesired(boolean on) {
        if (!available()) {
            throw new BusinessException("Comanda aplicației de test nu e disponibilă pe acest server");
        }
        Path tmp = dir.resolve(DESIRED + ".tmp");
        try {
            Files.writeString(tmp, on ? "on\n" : "off\n", StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve(DESIRED), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new BusinessException("Starea aplicației de test nu se poate salva. Pe server rulați o dată deploy/test-control-setup.sh");
        }
    }

    public Status status() {
        if (!available()) {
            return new Status(State.UNKNOWN, null);
        }
        try {
            String[] parts = Files.readString(dir.resolve(STATUS), StandardCharsets.UTF_8).trim().split("\\s+");
            State state = switch (parts[0]) {
                case "running" -> State.RUNNING;
                case "starting" -> State.STARTING;
                case "stopped" -> State.STOPPED;
                default -> State.UNKNOWN;
            };
            Instant checked = null;
            if (parts.length > 1) {
                try {
                    checked = OffsetDateTime.parse(parts[1]).toInstant();
                } catch (DateTimeParseException ignored) {
                    checked = null;
                }
            }
            return new Status(state, checked);
        } catch (IOException e) {
            return new Status(State.UNKNOWN, null);
        }
    }
}
