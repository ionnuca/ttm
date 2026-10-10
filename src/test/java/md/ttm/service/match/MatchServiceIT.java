package md.ttm.service.match;

import md.ttm.IntegrationTest;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.repository.PlayerRepository;
import md.ttm.service.tournament.MatchResultForm;
import md.ttm.service.tournament.TournamentForm;
import md.ttm.service.tournament.TournamentService;
import md.ttm.service.tournament.TournamentSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class MatchServiceIT {

    @Autowired
    MatchService matchService;
    @Autowired
    TournamentService tournamentService;
    @Autowired
    PlayerRepository playerRepository;

    @Test
    void ultimeleMeciuriSiStatisticaJucatorului() {
        Player ana = player("Ana", 1300);
        Player ion = player("Ion", 1200);
        Player dan = player("Dan", 1100);
        Tournament tournament = tournamentService.create(new TournamentForm("Cupa statisticii", LocalDate.of(2026, 11, 3)));
        List.of(ana, ion, dan).forEach(p -> tournamentService.addParticipant(tournament.getId(), p.getId()));
        tournamentService.start(tournament.getId(), TournamentSettings.roundRobin(5));
        List<TournamentMatch> matches = tournamentService.findDetails(tournament.getId()).orElseThrow().matches();

        // Ana câștigă 3:1 și tehnic; Ion – Dan 3:2
        for (TournamentMatch m : matches) {
            boolean anaIsA = m.getParticipantA().getPlayer().getId().equals(ana.getId());
            boolean anaIsB = m.getParticipantB().getPlayer().getId().equals(ana.getId());
            if (anaIsA || anaIsB) {
                boolean danPlays = m.getParticipantA().getPlayer().getId().equals(dan.getId())
                        || m.getParticipantB().getPlayer().getId().equals(dan.getId());
                if (danPlays) {
                    Long anaParticipant = anaIsA ? m.getParticipantA().getId() : m.getParticipantB().getId();
                    tournamentService.recordResult(m.getId(), MatchResultForm.walkover(anaParticipant));
                } else {
                    tournamentService.recordResult(m.getId(), anaIsA ? MatchResultForm.sets(3, 1) : MatchResultForm.sets(1, 3));
                }
            } else {
                boolean ionIsA = m.getParticipantA().getPlayer().getId().equals(ion.getId());
                tournamentService.recordResult(m.getId(), ionIsA ? MatchResultForm.sets(3, 2) : MatchResultForm.sets(2, 3));
            }
        }

        List<MatchSummary> recent = matchService.findRecent(10);
        assertThat(recent).hasSizeGreaterThanOrEqualTo(3);
        assertThat(recent.subList(0, 3)).allSatisfy(m -> assertThat(m.tournamentName()).isEqualTo("Cupa statisticii"));
        assertThat(recent.subList(0, 3)).allSatisfy(m -> assertThat(m.rated()).isTrue());

        PlayerProfile anaProfile = matchService.findPlayerProfile(ana.getId()).orElseThrow();
        PlayerStats stats = anaProfile.stats();
        assertThat(anaProfile.matches()).hasSize(2);
        assertThat(stats.wins()).isEqualTo(1);
        assertThat(stats.walkoverWins()).isEqualTo(1);
        assertThat(stats.losses()).isZero();
        assertThat(stats.setsWon()).isEqualTo(3);
        assertThat(stats.setsLost()).isEqualTo(1);
        assertThat(stats.winPercent()).isEqualTo(100);
        assertThat(stats.tournaments()).isEqualTo(1);
        assertThat(stats.bestRating()).isGreaterThan(1300);
        assertThat(anaProfile.rank()).isGreaterThanOrEqualTo(1);

        PlayerProfile ionProfile = matchService.findPlayerProfile(ion.getId()).orElseThrow();
        assertThat(ionProfile.stats().wins()).isEqualTo(1);
        assertThat(ionProfile.stats().losses()).isEqualTo(1);
        assertThat(ionProfile.stats().winPercent()).isEqualTo(50);
        MatchSummary.Perspective versusAna = ionProfile.matches().stream()
                .map(m -> m.from(ion.getId()))
                .filter(p -> p.opponent().getId().equals(ana.getId()))
                .findFirst().orElseThrow();
        assertThat(versusAna.won()).isFalse();
        assertThat(versusAna.ownSets()).isEqualTo(1);
        assertThat(versusAna.opponentSets()).isEqualTo(3);

        assertThat(matchService.findPlayerProfile(-1L)).isEmpty();
    }

    private Player player(String firstName, int rating) {
        Player player = new Player(firstName, "Statistică");
        player.setInitialRating(rating);
        player.setRating(rating);
        return playerRepository.save(player);
    }
}
