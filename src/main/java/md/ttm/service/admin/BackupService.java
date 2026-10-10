package md.ttm.service.admin;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backup-ul bazei de date, generat de aplicație (administrator), ca fișier SQL.
 * <p>
 * Conține datele tuturor tabelelor (în formatul COPY al PostgreSQL, ca {@code pg_dump}), într-o ordine
 * care respectă cheile străine, plus repoziționarea secvențelor. Schema nu e inclusă: o creează Flyway
 * la pornirea aplicației. La restaurare, fișierul verifică întâi că versiunea schemei e aceeași,
 * apoi înlocuiește toate datele, într-o singură tranzacție:
 * <pre>docker compose exec -T db psql -U ttm -d ttm &lt; ttm-backup-....sql</pre>
 */
@Service
public class BackupService {

    static final ZoneId ZONE = ZoneId.of("Europe/Chisinau");
    private static final String SCHEMA = "public";
    private static final String FLYWAY_TABLE = "flyway_schema_history";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<BuildProperties> buildProperties;

    public BackupService(DataSource dataSource, ObjectProvider<BuildProperties> buildProperties) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.buildProperties = buildProperties;
    }

    public static String fileName(ZonedDateTime at) {
        return "ttm-backup-" + at.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".sql";
    }

    /** Backup-ul complet al datelor, la momentul apelului (instantaneu consistent al bazei). */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] createBackup() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        List<String> tables = tablesInDependencyOrder();
        String schemaVersion = jdbc.queryForObject(
                "select version from " + FLYWAY_TABLE + " where success order by installed_rank desc limit 1",
                String.class);
        BuildProperties build = buildProperties.getIfAvailable();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringBuilder head = new StringBuilder()
                .append("-- TTM – backup al bazei de date\n")
                .append("-- Creat: ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append(" (Europe/Chisinau)\n")
                .append("-- Aplicația: ").append(build != null ? build.getVersion() : "necunoscută")
                .append(" · versiunea schemei (Flyway): ").append(schemaVersion).append('\n')
                .append("--\n")
                .append("-- Restaurare (înlocuiește TOATE datele cu cele din acest fișier):\n")
                .append("--   docker compose exec -T db psql -U ttm -d ttm < ").append(fileName(now)).append('\n')
                .append("-- Baza trebuie să aibă aceeași versiune a schemei; altfel restaurarea se oprește fără modificări.\n\n")
                .append("\\set ON_ERROR_STOP on\n")
                .append("SET client_encoding = 'UTF8';\n")
                .append("BEGIN;\n\n")
                .append("DO $$\nBEGIN\n")
                .append("  IF (SELECT version FROM ").append(FLYWAY_TABLE)
                .append(" WHERE success ORDER BY installed_rank DESC LIMIT 1) IS DISTINCT FROM '")
                .append(schemaVersion).append("' THEN\n")
                .append("    RAISE EXCEPTION 'Versiunea schemei diferă de cea a backup-ului (").append(schemaVersion).append(")';\n")
                .append("  END IF;\nEND $$;\n\n");
        if (!tables.isEmpty()) {
            head.append("TRUNCATE TABLE ").append(String.join(", ", tables.stream().map(BackupService::qualified).toList()))
                    .append(" RESTART IDENTITY CASCADE;\n\n");
        }
        write(out, head);

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            CopyManager copy = connection.unwrap(PGConnection.class).getCopyAPI();
            for (String table : tables) {
                String columns = String.join(", ", columns(table).stream().map(BackupService::quote).toList());
                write(out, "COPY " + qualified(table) + " (" + columns + ") FROM stdin;\n");
                copy.copyOut("COPY " + qualified(table) + " (" + columns + ") TO STDOUT", out);
                write(out, "\\.\n\n");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Backup-ul nu a putut fi creat: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        StringBuilder tail = new StringBuilder();
        for (Map.Entry<String, String> identity : identityColumns().entrySet()) {
            String table = qualified(identity.getKey());
            String column = quote(identity.getValue());
            tail.append("SELECT setval(pg_get_serial_sequence('").append(table.replace("'", "''")).append("', '")
                    .append(identity.getValue()).append("'), COALESCE((SELECT max(").append(column)
                    .append(") FROM ").append(table).append("), 0) + 1, false);\n");
        }
        tail.append("\nCOMMIT;\n");
        write(out, tail);
        return out.toByteArray();
    }

    /** Tabelele schemei (fără istoricul Flyway), cele referite înaintea celor care le referă. */
    List<String> tablesInDependencyOrder() {
        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = ? and table_type = 'BASE TABLE' and table_name <> ?
                order by table_name""", String.class, SCHEMA, FLYWAY_TABLE);
        Map<String, Set<String>> dependsOn = new LinkedHashMap<>();
        tables.forEach(t -> dependsOn.put(t, new LinkedHashSet<>()));
        jdbc.query("""
                select child.relname as child, parent.relname as parent
                from pg_constraint c
                  join pg_class child on child.oid = c.conrelid
                  join pg_class parent on parent.oid = c.confrelid
                  join pg_namespace n on n.oid = child.relnamespace
                where c.contype = 'f' and n.nspname = ?""", (RowCallbackHandler) rs -> {
            String child = rs.getString("child");
            String parent = rs.getString("parent");
            if (dependsOn.containsKey(child) && dependsOn.containsKey(parent) && !child.equals(parent)) {
                dependsOn.get(child).add(parent);
            }
        }, SCHEMA);

        List<String> ordered = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        while (ordered.size() < tables.size()) {
            boolean progress = false;
            for (String table : tables) {
                if (!done.contains(table) && done.containsAll(dependsOn.get(table))) {
                    ordered.add(table);
                    done.add(table);
                    progress = true;
                }
            }
            if (!progress) { // dependențe circulare: restul în ordine alfabetică
                tables.stream().filter(t -> !done.contains(t)).forEach(t -> {
                    ordered.add(t);
                    done.add(t);
                });
            }
        }
        return ordered;
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_schema = ? and table_name = ?
                order by ordinal_position""", String.class, SCHEMA, table);
    }

    private Map<String, String> identityColumns() {
        Map<String, String> identity = new LinkedHashMap<>();
        jdbc.query("""
                select table_name, column_name from information_schema.columns
                where table_schema = ? and is_identity = 'YES' and table_name <> ?
                order by table_name""", (RowCallbackHandler) rs -> {
            identity.put(rs.getString("table_name"), rs.getString("column_name"));
        }, SCHEMA, FLYWAY_TABLE);
        return identity;
    }

    private static String qualified(String table) {
        return SCHEMA + "." + quote(table);
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static void write(ByteArrayOutputStream out, CharSequence text) {
        out.writeBytes(text.toString().getBytes(StandardCharsets.UTF_8));
    }
}
