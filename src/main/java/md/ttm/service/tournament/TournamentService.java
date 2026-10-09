package md.ttm.service.tournament;

import md.ttm.common.BusinessException;
import md.ttm.common.Texts;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.MatchOutcome;
import md.ttm.model.tournament.PrizeDistribution;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.model.user.AppUser;
import md.ttm.model.rating.RatingHistory;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.RatingHistoryRepository;
import md.ttm.repository.PlayerRepository;
import md.ttm.repository.TournamentMatchRepository;
import md.ttm.repository.TournamentParticipantRepository;
import md.ttm.repository.TournamentRepository;
import md.ttm.security.SecurityUtils;
import md.ttm.service.rating.RatingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turneele: creare, înscriere, începere, rezultate și clasament.
 * <ul>
 *     <li>Oricine poate vedea turneele.</li>
 *     <li>Utilizatorul logat se poate înscrie / retrage cât timp înscrierea e deschisă.</li>
 *     <li>Participanții unui turneu început pot introduce rezultatele meciurilor lui.</li>
 *     <li>Administratorul face orice: creează turneul (nume și dată), îl pornește alegând
 *     configurarea (tip, seturi, turneu comercial), corectează rezultate.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TournamentService {

    private static final Set<Integer> ALLOWED_BEST_OF = Set.of(3, 5, 7);

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentMatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final AppUserRepository userRepository;
    private final RatingHistoryRepository historyRepository;
    private final RatingService ratingService;

    public TournamentService(TournamentRepository tournamentRepository,
                             TournamentParticipantRepository participantRepository,
                             TournamentMatchRepository matchRepository,
                             PlayerRepository playerRepository,
                             AppUserRepository userRepository,
                             RatingHistoryRepository historyRepository,
                             RatingService ratingService) {
        this.historyRepository = historyRepository;
        this.ratingService = ratingService;
        this.tournamentRepository = tournamentRepository;
        this.participantRepository = participantRepository;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------
    // Citire
    // ------------------------------------------------------------------

    /** Toate turneele, cele mai recente primele. */
    public List<TournamentSummary> findAll() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : participantRepository.countByTournament()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return tournamentRepository.findAllByOrderByTournamentDateDescIdDesc().stream()
                .map(t -> new TournamentSummary(t, counts.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    public Optional<TournamentDetails> findDetails(Long tournamentId) {
        return tournamentRepository.findById(tournamentId).map(this::details);
    }

    /** Jucătorii care pot fi adăugați de administrator (încă neînscriși), după rating. */
    @PreAuthorize("hasRole('ADMIN')")
    public List<Player> findPlayersNotRegistered(Long tournamentId) {
        Set<Long> registered = new HashSet<>();
        participantRepository.findByTournamentIdWithPlayer(tournamentId)
                .forEach(p -> registered.add(p.getPlayer().getId()));
        return playerRepository.findAllByOrderByRatingDescLastNameAscFirstNameAsc().stream()
                .filter(p -> !registered.contains(p.getId()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Administrare turneu
    // ------------------------------------------------------------------

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Tournament create(TournamentForm form) {
        Tournament tournament = new Tournament();
        apply(form, tournament);
        return tournamentRepository.save(tournament);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Tournament update(Long tournamentId, TournamentForm form) {
        Tournament tournament = requireTournament(tournamentId);
        if (!tournament.isRegistrationOpen()) {
            throw new BusinessException("Turneul a început; datele lui nu mai pot fi modificate");
        }
        apply(form, tournament);
        return tournamentRepository.save(tournament);
    }

    /** Șterge turneul cu toți participanții și meciurile lui. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        boolean rated = tournament.getStatus() == TournamentStatus.FINISHED;
        tournamentRepository.delete(tournament);
        tournamentRepository.flush();
        if (rated) {
            ratingService.recalculateAll();
        }
    }

    // ------------------------------------------------------------------
    // Înscriere
    // ------------------------------------------------------------------

    /** Utilizatorul curent se înscrie la turneu. */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public TournamentParticipant registerSelf(Long tournamentId) {
        Player player = currentPlayer()
                .orElseThrow(() -> new BusinessException("Contul nu are un profil de jucător"));
        return register(requireTournament(tournamentId), player);
    }

    /** Utilizatorul curent se retrage de la turneu (doar înainte de începere). */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void unregisterSelf(Long tournamentId) {
        Tournament tournament = requireOpenTournament(tournamentId);
        Player player = currentPlayer()
                .orElseThrow(() -> new BusinessException("Contul nu are un profil de jucător"));
        TournamentParticipant participant = participantRepository
                .findByTournamentIdAndPlayerId(tournament.getId(), player.getId())
                .orElseThrow(() -> new BusinessException("Nu sunteți înscris la acest turneu"));
        participantRepository.delete(participant);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public TournamentParticipant addParticipant(Long tournamentId, Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new BusinessException("Jucătorul nu mai există"));
        return register(requireTournament(tournamentId), player);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void removeParticipant(Long tournamentId, Long participantId) {
        requireOpenTournament(tournamentId);
        TournamentParticipant participant = participantRepository.findById(participantId)
                .filter(p -> p.getTournament().getId().equals(tournamentId))
                .orElseThrow(() -> new BusinessException("Participantul nu mai este înscris"));
        participantRepository.delete(participant);
    }

    private TournamentParticipant register(Tournament tournament, Player player) {
        if (!tournament.isRegistrationOpen()) {
            throw new BusinessException("Înscrierea la acest turneu este închisă");
        }
        if (participantRepository.existsByTournamentIdAndPlayerId(tournament.getId(), player.getId())) {
            throw new BusinessException(player.getDisplayName() + " este deja înscris la acest turneu");
        }
        return participantRepository.save(new TournamentParticipant(tournament, player));
    }

    // ------------------------------------------------------------------
    // Începerea turneului
    // ------------------------------------------------------------------

    /**
     * Stabilește configurarea turneului (tip, seturi, turneu comercial), închide înscrierea,
     * formează grupa (jucătorii ordonați după rating, descrescător) și generează toate meciurile.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void start(Long tournamentId, TournamentSettings settings) {
        Tournament tournament = requireOpenTournament(tournamentId);
        List<TournamentParticipant> participants = new ArrayList<>(participantRepository.findByTournamentIdWithPlayer(tournamentId));
        if (participants.size() < 2) {
            throw new BusinessException("Pentru a începe turneul sunt necesari cel puțin 2 participanți");
        }
        applySettings(settings, tournament);
        participants.sort(BY_RATING);
        for (int i = 0; i < participants.size(); i++) {
            TournamentParticipant participant = participants.get(i);
            participant.setSeed(i + 1);
            participant.setSeedRating(participant.getPlayer().getRating());
        }
        participantRepository.saveAll(participants);

        List<TournamentMatch> matches = new ArrayList<>();
        for (RoundRobinScheduler.Pairing pairing : RoundRobinScheduler.schedule(participants.size())) {
            matches.add(new TournamentMatch(tournament, pairing.round(),
                    participants.get(pairing.first()), participants.get(pairing.second())));
        }
        matchRepository.saveAll(matches);

        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournament.setStartedAt(Instant.now());
        tournamentRepository.save(tournament);
    }

    // ------------------------------------------------------------------
    // Rezultate
    // ------------------------------------------------------------------

    /**
     * Înregistrează (sau corectează) rezultatul unui meci. Permis administratorului și,
     * cât timp turneul e în desfășurare, oricărui participant al turneului.
     * Când toate meciurile au rezultat, turneul trece automat în starea „Încheiat”.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public TournamentMatch recordResult(Long matchId, MatchResultForm result) {
        TournamentMatch match = matchRepository.findByIdWithParticipants(matchId)
                .orElseThrow(() -> new BusinessException("Meciul nu mai există"));
        Tournament tournament = match.getTournament();
        requireCanRecord(tournament);

        if (result == null || result.outcome() == null) {
            throw new BusinessException("Alegeți tipul rezultatului");
        }
        if (result.outcome() == MatchOutcome.NORMAL) {
            int setsToWin = tournament.getSetsToWin();
            Integer a = result.setsA();
            Integer b = result.setsB();
            boolean valid = a != null && b != null && a >= 0 && b >= 0
                    && ((a == setsToWin && b < setsToWin) || (b == setsToWin && a < setsToWin));
            if (!valid) {
                throw new BusinessException("Scor invalid: câștigătorul trebuie să aibă " + setsToWin
                        + " seturi, iar adversarul mai puține");
            }
            match.setSetsA(a);
            match.setSetsB(b);
            match.setWinner(a > b ? match.getParticipantA() : match.getParticipantB());
        } else {
            Long winnerId = result.walkoverWinner();
            TournamentParticipant winner;
            if (match.getParticipantA().getId().equals(winnerId)) {
                winner = match.getParticipantA();
            } else if (match.getParticipantB().getId().equals(winnerId)) {
                winner = match.getParticipantB();
            } else {
                throw new BusinessException("Alegeți jucătorul care câștigă tehnic");
            }
            match.setSetsA(null);
            match.setSetsB(null);
            match.setWinner(winner);
        }
        match.setOutcome(result.outcome());
        match.setRecordedBy(SecurityUtils.currentUsername().orElse(null));
        match.setRecordedAt(Instant.now());
        matchRepository.saveAndFlush(match);

        if (tournament.getStatus() == TournamentStatus.IN_PROGRESS
                && matchRepository.countByTournamentIdAndOutcomeIsNull(tournament.getId()) == 0) {
            tournament.setStatus(TournamentStatus.FINISHED);
            tournament.setFinishedAt(Instant.now());
            tournamentRepository.saveAndFlush(tournament);
        }
        // turneu încheiat acum sau rezultat corectat într-un turneu încheiat: ratingul se recalculează
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            ratingService.recalculateAll();
        }
        return match;
    }

    /** Șterge rezultatul unui meci (administrator). Un turneu încheiat redevine „în desfășurare”. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void clearResult(Long matchId) {
        TournamentMatch match = matchRepository.findByIdWithParticipants(matchId)
                .orElseThrow(() -> new BusinessException("Meciul nu mai există"));
        match.clearResult();
        matchRepository.save(match);
        Tournament tournament = match.getTournament();
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            tournament.setStatus(TournamentStatus.IN_PROGRESS);
            tournament.setFinishedAt(null);
            tournamentRepository.saveAndFlush(tournament);
            // turneul redeschis nu mai contează la rating până la încheiere
            ratingService.recalculateAll();
        }
    }

    // ------------------------------------------------------------------
    // Ștergerea jucătorilor
    // ------------------------------------------------------------------

    /**
     * Pregătește ștergerea unui jucător: îl scoate din turneele încă neîncepute. Dacă a jucat
     * într-un turneu început, ștergerea e refuzată, ca rezultatele să rămână complete.
     */
    @Transactional
    public void releasePlayer(Long playerId) {
        if (participantRepository.existsByPlayerIdAndTournamentStatusNot(playerId, TournamentStatus.REGISTRATION)) {
            throw new BusinessException("Jucătorul a participat la turnee începute și nu poate fi șters");
        }
        participantRepository.deleteAll(participantRepository.findByPlayerId(playerId));
        participantRepository.flush();
    }

    // ------------------------------------------------------------------

    private TournamentDetails details(Tournament tournament) {
        List<TournamentParticipant> participants = new ArrayList<>(participantRepository.findByTournamentIdWithPlayer(tournament.getId()));
        List<TournamentMatch> matches = tournament.isStarted()
                ? matchRepository.findByTournamentIdWithParticipants(tournament.getId())
                : List.of();
        if (tournament.isStarted()) {
            participants.sort(Comparator.comparing(TournamentParticipant::getSeed));
        } else {
            participants.sort(BY_RATING);
        }

        List<StandingsCalculator.Row> standings = tournament.isStarted()
                ? standings(tournament, participants, matches)
                : List.of();

        BigDecimal pool = null;
        List<PrizePlace> prizes = List.of();
        PrizeDistribution distribution = tournament.getPrizeDistribution();
        if (distribution != null && tournament.getEntryFee() != null) {
            pool = tournament.getEntryFee().multiply(BigDecimal.valueOf(participants.size()));
            prizes = prizes(tournament, distribution, pool, participants, standings);
        }

        Optional<Player> currentPlayer = currentPlayer();
        TournamentParticipant own = currentPlayer
                .flatMap(player -> participants.stream()
                        .filter(p -> p.getPlayer().getId().equals(player.getId()))
                        .findFirst())
                .orElse(null);
        boolean admin = SecurityUtils.isAdmin();
        boolean canSelfRegister = tournament.isRegistrationOpen() && currentPlayer.isPresent() && own == null;
        boolean canRecord = (tournament.getStatus() == TournamentStatus.IN_PROGRESS && (admin || own != null))
                || (tournament.getStatus() == TournamentStatus.FINISHED && admin);

        Map<Long, RatingHistory> ratingChanges = new HashMap<>();
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            historyRepository.findByTournamentId(tournament.getId())
                    .forEach(h -> ratingChanges.put(h.getPlayer().getId(), h));
        }

        return new TournamentDetails(tournament, participants, matches, standings, pool, prizes, own,
                admin, canSelfRegister, canRecord, ratingChanges);
    }

    private static List<StandingsCalculator.Row> standings(Tournament tournament,
                                                           List<TournamentParticipant> participants,
                                                           List<TournamentMatch> matches) {
        List<StandingsCalculator.Competitor> competitors = participants.stream()
                .map(p -> new StandingsCalculator.Competitor(p.getId(), p.getSeed()))
                .toList();
        List<StandingsCalculator.Result> results = matches.stream()
                .filter(TournamentMatch::isPlayed)
                .map(m -> new StandingsCalculator.Result(
                        m.getParticipantA().getId(), m.getParticipantB().getId(),
                        m.getSetsA() != null ? m.getSetsA() : 0, m.getSetsB() != null ? m.getSetsB() : 0,
                        m.getWinner().getId(), m.isWalkover()))
                .toList();
        return StandingsCalculator.compute(competitors, results, tournament.getSetsToWin());
    }

    private static List<PrizePlace> prizes(Tournament tournament, PrizeDistribution distribution, BigDecimal pool,
                                           List<TournamentParticipant> participants,
                                           List<StandingsCalculator.Row> standings) {
        Map<Integer, String> namesByPlace = new HashMap<>();
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            Map<Long, String> names = new HashMap<>();
            participants.forEach(p -> names.put(p.getId(), p.getPlayer().getDisplayName()));
            standings.forEach(row -> namesByPlace.put(row.place(), names.get(row.id())));
        }
        List<BigDecimal> amounts = distribution.split(pool);
        List<Integer> percentages = distribution.percentages();
        List<PrizePlace> prizes = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            prizes.add(new PrizePlace(i + 1, percentages.get(i), amounts.get(i), namesByPlace.get(i + 1)));
        }
        return prizes;
    }

    private void requireCanRecord(Tournament tournament) {
        if (tournament.getStatus() == TournamentStatus.REGISTRATION) {
            throw new BusinessException("Turneul nu a început încă");
        }
        if (SecurityUtils.isAdmin()) {
            return;
        }
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            throw new BusinessException("Turneul s-a încheiat; rezultatele pot fi corectate doar de administrator");
        }
        boolean participant = currentPlayer()
                .map(player -> participantRepository.existsByTournamentIdAndPlayerId(tournament.getId(), player.getId()))
                .orElse(false);
        if (!participant) {
            throw new BusinessException("Doar participanții turneului pot introduce rezultate");
        }
    }

    private static void apply(TournamentForm form, Tournament tournament) {
        String name = Texts.trimToNull(form.getName());
        if (name == null) {
            throw new BusinessException("Introduceți numele turneului");
        }
        if (name.length() > 150) {
            throw new BusinessException("Numele turneului poate avea cel mult 150 de caractere");
        }
        if (form.getDate() == null) {
            throw new BusinessException("Alegeți data turneului");
        }
        tournament.setName(name);
        tournament.setTournamentDate(form.getDate());
    }

    /** Validează întâi toată configurarea, apoi o aplică (turneul nu rămâne pe jumătate modificat). */
    private static void applySettings(TournamentSettings settings, Tournament tournament) {
        if (settings == null || settings.getFormat() == null) {
            throw new BusinessException("Alegeți tipul turneului");
        }
        if (settings.getBestOf() == null || !ALLOWED_BEST_OF.contains(settings.getBestOf())) {
            throw new BusinessException("Numărul de seturi trebuie să fie 3, 5 sau 7");
        }
        Integer winners = null;
        BigDecimal fee = null;
        if (settings.isCommercial()) {
            winners = settings.getWinnersCount();
            if (winners == null || winners < 1 || winners > 3) {
                throw new BusinessException("Alegeți numărul de câștigători (1, 2 sau 3)");
            }
            if (settings.getEntryFee() == null || settings.getEntryFee().signum() <= 0) {
                throw new BusinessException("Introduceți taxa de participare");
            }
            fee = settings.getEntryFee().setScale(2, RoundingMode.HALF_UP);
        }
        tournament.setFormat(settings.getFormat());
        tournament.setBestOf(settings.getBestOf());
        tournament.setCommercial(settings.isCommercial());
        tournament.setWinnersCount(winners);
        tournament.setEntryFee(fee);
    }

    private Tournament requireTournament(Long tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException("Turneul nu mai există"));
    }

    private Tournament requireOpenTournament(Long tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (!tournament.isRegistrationOpen()) {
            throw new BusinessException("Turneul a început deja; înscrierea este închisă");
        }
        return tournament;
    }

    private Optional<Player> currentPlayer() {
        return SecurityUtils.currentUsername()
                .flatMap(userRepository::findByUsername)
                .map(AppUser::getPlayer);
    }

    /** Ordinea din grupă: rating descrescător, apoi alfabetic. */
    private static final Comparator<TournamentParticipant> BY_RATING = Comparator
            .comparingInt((TournamentParticipant p) -> -p.getPlayer().getRating())
            .thenComparing(p -> p.getPlayer().getLastName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(p -> p.getPlayer().getFirstName(), String.CASE_INSENSITIVE_ORDER);
}
