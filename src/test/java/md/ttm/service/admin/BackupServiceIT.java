package md.ttm.service.admin;

import md.ttm.IntegrationTest;
import md.ttm.model.player.Player;
import md.ttm.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class BackupServiceIT {

    @Autowired
    BackupService backupService;
    @Autowired
    PlayerRepository playerRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void backupulContineToateTabeleleInOrdineaCheilorStraine() {
        playerRepository.saveAndFlush(new Player("Backup", "Țestescu"));

        String sql = new String(backupService.createBackup(), StandardCharsets.UTF_8);

        assertThat(sql).startsWith("-- TTM – backup al bazei de date");
        assertThat(sql).contains("BEGIN;", "TRUNCATE TABLE", "RESTART IDENTITY CASCADE;", "COMMIT;");
        assertThat(sql).contains("COPY public.\"player\" (").contains("COPY public.\"app_user\" (")
                .contains("COPY public.\"tournament_match\" (").contains("COPY public.\"player_photo\" (");
        assertThat(sql).doesNotContain("COPY public.\"flyway_schema_history\"");
        assertThat(sql).contains("Țestescu");
        assertThat(sql).contains("setval(pg_get_serial_sequence('public.\"player\"', 'id')");

        List<String> order = backupService.tablesInDependencyOrder();
        assertThat(order.indexOf("player")).isLessThan(order.indexOf("app_user"));
        assertThat(order.indexOf("tournament")).isLessThan(order.indexOf("tournament_participant"));
        assertThat(order.indexOf("tournament_participant")).isLessThan(order.indexOf("tournament_match"));
        assertThat(order.indexOf("player")).isLessThan(order.indexOf("player_photo"));
    }

    @Test
    @WithMockUser(username = "ana", roles = "USER")
    void doarAdministratorulFaceBackup() {
        assertThatThrownBy(() -> backupService.createBackup()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void numeleFisieruluiContineDataSiOra() {
        assertThat(BackupService.fileName(java.time.ZonedDateTime.of(2026, 10, 10, 19, 5, 7, 0, BackupService.ZONE)))
                .isEqualTo("ttm-backup-2026-10-10-190507.sql");
    }
}
