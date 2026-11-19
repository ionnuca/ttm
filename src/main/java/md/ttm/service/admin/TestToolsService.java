package md.ttm.service.admin;

import md.ttm.common.BusinessException;
import md.ttm.model.player.PlayHand;
import md.ttm.model.player.PlayStyle;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.repository.PlayerRepository;
import md.ttm.service.tournament.GroupView;
import md.ttm.service.tournament.MatchResultForm;
import md.ttm.service.tournament.StandingsCalculator;
import md.ttm.service.tournament.TournamentDetails;
import md.ttm.service.tournament.TournamentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Unelte doar pentru aplicația de test ({@code app.test-tools.enabled=true}): jucători aleatori și rezultate
 * aleatorii pentru meciurile unui turneu. În producție sunt oprite: butoanele nu apar, iar apelurile se refuză.
 */
@Service
public class TestToolsService {

    private static final String[] FIRST_NAMES = {
            "Andrei", "Alexandru", "Ion", "Mihai", "Vasile", "Sergiu", "Victor", "Nicolai", "Dumitru", "Igor",
            "Vadim", "Cristian", "Radu", "Petru", "Eugen", "Valeriu", "Oleg", "Denis", "Maxim", "Artiom",
            "Ana", "Maria", "Elena", "Irina", "Natalia", "Cristina", "Olga", "Tatiana", "Diana", "Daniela"};
    private static final String[] LAST_NAMES = {
            "Popescu", "Rusu", "Ceban", "Lungu", "Botnari", "Gâlcă", "Munteanu", "Rotaru", "Cojocaru", "Ursu",
            "Țurcanu", "Melnic", "Sârbu", "Bivol", "Moraru", "Croitoru", "Cebotari", "Lupu", "Guțu", "Ciobanu",
            "Barbă", "Plămădeală", "Răileanu", "Grosu", "Darii", "Cazacu", "Rață", "Prodan", "Balan", "Ghețu"};
    private static final String[] CITIES = {"Chișinău", "Bălți", "Orhei", "Cahul", "Ungheni", "Soroca", "Ialoveni", "Comrat"};
    private static final String[] BLADES = {"Butterfly Viscaria", "Stiga Clipper", "DHS Hurricane Long 5", "Tibhar Stratus", "Yasaka Ma Lin Extra Offensive"};
    private static final String[] RUBBERS = {"Tenergy 05", "Dignics 09C", "Hurricane 3", "Rasanter R47", "Mark V", "Evolution MX-P"};

    private final boolean enabled;
    private final PlayerRepository playerRepository;
    private final TournamentService tournamentService;

    public TestToolsService(@Value("${app.test-tools.enabled:false}") boolean enabled,
                            PlayerRepository playerRepository, TournamentService tournamentService) {
        this.enabled = enabled;
        this.playerRepository = playerRepository;
        this.tournamentService = tournamentService;
    }

    /** Uneltele sunt pornite doar în aplicația de test. */
    public boolean enabled() {
        return enabled;
    }

    /** Creează {@code count} jucători cu nume, oraș, stil, echipament și rating (800–1700) aleatori. */
    @PreAuthorize("hasRole('ADMIN')")
    public int createRandomPlayers(int count) {
        requireEnabled();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Player player = new Player(pick(FIRST_NAMES), pick(LAST_NAMES));
            player.setCity(pick(CITIES));
            player.setPlayStyle(random.nextInt(5) == 0 ? PlayStyle.DEFENCE : PlayStyle.ATTACK);
            player.setPlayHand(random.nextInt(6) == 0 ? PlayHand.LEFT : PlayHand.RIGHT);
            player.setBlade(pick(BLADES));
            player.setForehandRubber(pick(RUBBERS));
            player.setBackhandRubber(pick(RUBBERS));
            int rating = 800 + random.nextInt(901);
            player.setInitialRating(rating);
            player.setRating(rating);
            players.add(player);
        }
        playerRepository.saveAll(players);
        return players.size();
    }

    /**
     * Completează cu rezultate aleatorii toate meciurile fără rezultat ale turneului și seturile de departajare de
     * jucat, inclusiv meciurile care apar în tablou pe parcurs. Jucătorul cu rating mai mare câștigă mai des.
     * Nu trece la etapa următoare și nu încheie turneul.
     *
     * @return numărul de meciuri și seturi de departajare completate
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    public int fillResults(Long tournamentId) {
        requireEnabled();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int filled = 0;
        for (int pass = 0; pass < 200; pass++) {
            TournamentDetails details = tournamentService.findDetails(tournamentId)
                    .orElseThrow(() -> new BusinessException("Turneul nu mai există"));
            if (details.tournament().getStatus() != TournamentStatus.IN_PROGRESS) {
                throw new BusinessException("Rezultatele se completează doar la un turneu în desfășurare");
            }
            int setsToWin = details.tournament().getSetsToWin();
            int before = filled;
            for (TournamentMatch match : details.matches()) {
                if (match.isPlayed()) {
                    continue;
                }
                int ratingA = match.getParticipantA().getPlayer().getRating();
                int ratingB = match.getParticipantB().getPlayer().getRating();
                // 50% la rating egal, până la ~85% pentru favorit
                double chanceA = 1 / (1 + Math.pow(10, (ratingB - ratingA) / 400.0));
                boolean aWins = random.nextDouble() < chanceA;
                int loserSets = random.nextInt(setsToWin);
                tournamentService.recordResult(match.getId(), aWins
                        ? MatchResultForm.sets(setsToWin, loserSets)
                        : MatchResultForm.sets(loserSets, setsToWin));
                filled++;
            }
            if (filled > before) {
                continue; // tablourile pot avea acum meciuri noi
            }
            for (StandingsCalculator.Pairing p : details.pendingTiebreaks()) {
                filled += tiebreak(tournamentId, null, p, random);
            }
            for (GroupView group : details.groups()) {
                for (StandingsCalculator.Pairing p : group.pendingTiebreaks()) {
                    filled += tiebreak(tournamentId, group.group().getId(), p, random);
                }
            }
            if (filled == before) {
                return filled;
            }
        }
        return filled;
    }

    private int tiebreak(Long tournamentId, Long groupId, StandingsCalculator.Pairing p, ThreadLocalRandom random) {
        int loser = random.nextInt(10);
        boolean aWins = random.nextBoolean();
        tournamentService.recordTiebreak(tournamentId, groupId, p.round(), p.a(), p.b(),
                aWins ? 11 : loser, aWins ? loser : 11);
        return 1;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessException("Uneltele de test sunt disponibile doar în aplicația de test");
        }
    }

    private static String pick(String[] values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
