package md.ttm.service.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestEnvironmentServiceTest {

    @TempDir
    Path dir;

    @Test
    void scrieStareaDoritaSiCiteșteStareaReala() throws Exception {
        TestEnvironmentService service = new TestEnvironmentService(dir.toString(), "https://test.example", "");
        assertThat(service.available()).isTrue();
        assertThat(service.serverServiceInstalled()).isFalse();
        assertThat(service.desiredOn()).isFalse();
        assertThat(service.status().state()).isEqualTo(TestEnvironmentService.State.UNKNOWN);

        service.setDesired(true);
        assertThat(Files.readString(dir.resolve("test-desired")).trim()).isEqualTo("on");
        assertThat(service.desiredOn()).isTrue();

        Files.writeString(dir.resolve("test-status"), "starting 2026-11-16T17:00:00+02:00\n");
        assertThat(service.serverServiceInstalled()).isTrue();
        assertThat(service.status().state()).isEqualTo(TestEnvironmentService.State.STARTING);
        assertThat(service.status().checkedAt()).isNotNull();

        service.setDesired(false);
        assertThat(service.desiredOn()).isFalse();
    }

    @Test
    void lipsesteInAplicatiaDeTestSauFaraFolder() {
        assertThat(new TestEnvironmentService(dir.toString(), "", "TEST").available()).isFalse();
        assertThat(new TestEnvironmentService("", "", "").available()).isFalse();
        assertThat(new TestEnvironmentService(dir.resolve("lipsa").toString(), "", "").available()).isFalse();
    }
}
