package md.ttm.service.tournament;

import md.ttm.common.BusinessException;
import md.ttm.common.Texts;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.MatchOutcome;
import md.ttm.model.tournament.PrizeDistribution;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentFormat;
import md.ttm.model.tournament.TournamentGroup;
import md.ttm.model.tournament.TournamentGroupMember;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.model.tournament.TournamentStage;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.model.tournament.KnockoutType;
import md.ttm.model.tournament.ParticipantStatus;
import md.ttm.model.notification.NotificationType;
import md.ttm.service.notification.NotificationService;
import md.ttm.model.tournament.TournamentTiebreak;
import md.ttm.repository.TournamentTiebreakRepository;
import md.ttm.model.user.AppUser;
import md.ttm.model.rating.RatingHistory;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.RatingHistoryRepository;
import md.ttm.repository.TournamentGroupMemberRepository;
import md.ttm.repository.TournamentGroupRepository;
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
import java.util.random.RandomGenerator;
import java.security.SecureRandom;
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
 *     <li>Administratorul și managerul de turnee fac orice cu turneele: creează turneul (nume și dată), îl pornește alegând
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
    private final TournamentGroupRepository groupRepository;
    private final TournamentGroupMemberRepository memberRepository;
    private final TournamentTiebreakRepository tiebreakRepository;
    private final NotificationService notificationService;

    public TournamentService(TournamentRepository tournamentRepository,
                             TournamentParticipantRepository participantRepository,
                             TournamentMatchRepository matchRepository,
                             PlayerRepository playerRepository,
                             AppUserRepository userRepository,
                             RatingHistoryRepository historyRepository,
                             RatingService ratingService,
                             TournamentGroupRepository groupRepository,
                             TournamentGroupMemberRepository memberRepository,
                             TournamentTiebreakRepository tiebreakRepository,
                             NotificationService notificationService) {
        this.tiebreakRepository = tiebreakRepository;
        this.notificationService = notificationService;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
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
        Map<Long, Long> pending = new HashMap<>();
        if (SecurityUtils.canManageTournaments()) {
            for (Object[] row : participantRepository.countPendingByTournament()) {
                pending.put((Long) row[0], (Long) row[1]);
            }
        }
        return tournamentRepository.findAllByOrderByTournamentDateDescIdDesc().stream()
                .map(t -> new TournamentSummary(t, counts.getOrDefault(t.getId(), 0L), pending.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    public Optional<TournamentDetails> findDetails(Long tournamentId) {
        return tournamentRepository.findById(tournamentId).map(this::details);
    }

    /** Jucătorii care pot fi adăugați de organizator (încă neînscriși), după rating. */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    public List<Player> findPlayersNotRegistered(Long tournamentId) {
        Set<Long> registered = new HashSet<>();
        participantRepository.findByTournamentIdWithPlayer(tournamentId)
                .forEach(p -> registered.add(p.getPlayer().getId()));
        // cererile neconfirmate rămân în listă: adăugarea de către organizator le confirmă
        return playerRepository.findAllByOrderByRatingDescLastNameAscFirstNameAsc().stream()
                .filter(p -> !registered.contains(p.getId()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Administrare turneu
    // ------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public Tournament create(TournamentForm form) {
        Tournament tournament = new Tournament();
        apply(form, tournament);
        return tournamentRepository.save(tournament);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public void delete(Long tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        boolean rated = tournament.getStatus() == TournamentStatus.FINISHED;
        tournamentRepository.deleteWithChildren(tournament.getId());
        if (rated) {
            ratingService.recalculateAll();
        }
    }

    // ------------------------------------------------------------------
    // Înscriere
    // ------------------------------------------------------------------

    /**
     * Utilizatorul curent cere înscrierea la turneu. Cererea așteaptă confirmarea unui organizator,
     * care primește o notificare; organizatorii (administrator, manager de turnee) și utilizatorii marcați
     * „fără confirmare” se înscriu direct.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public TournamentParticipant registerSelf(Long tournamentId) {
        Player player = currentPlayer()
                .orElseThrow(() -> new BusinessException("Contul nu are un profil de jucător"));
        Tournament tournament = requireTournament(tournamentId);
        boolean direct = SecurityUtils.canManageTournaments()
                || SecurityUtils.currentUsername().flatMap(userRepository::findByUsername).map(AppUser::isTrusted).orElse(false);
        TournamentParticipant participant = register(tournament, player,
                direct ? ParticipantStatus.CONFIRMED : ParticipantStatus.PENDING);
        if (!direct) {
            notificationService.notifyOrganizers(NotificationType.REGISTRATION_REQUESTED, tournament, player.getDisplayName());
        }
        return participant;
    }

    /** Organizatorul confirmă o cerere de înscriere; jucătorul primește o notificare. */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public void confirmParticipant(Long tournamentId, Long participantId) {
        Tournament tournament = requireOpenTournament(tournamentId);
        TournamentParticipant participant = requirePending(tournamentId, participantId);
        participant.setStatus(ParticipantStatus.CONFIRMED);
        participantRepository.save(participant);
        notificationService.notifyPlayer(participant.getPlayer(), NotificationType.REGISTRATION_CONFIRMED, tournament);
    }

    /** Organizatorul respinge o cerere de înscriere; jucătorul primește o notificare. */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public void rejectParticipant(Long tournamentId, Long participantId) {
        Tournament tournament = requireOpenTournament(tournamentId);
        TournamentParticipant participant = requirePending(tournamentId, participantId);
        participantRepository.delete(participant);
        notificationService.notifyPlayer(participant.getPlayer(), NotificationType.REGISTRATION_REJECTED, tournament);
    }

    private TournamentParticipant requirePending(Long tournamentId, Long participantId) {
        return participantRepository.findById(participantId)
                .filter(p -> p.getTournament().getId().equals(tournamentId) && !p.isConfirmed())
                .orElseThrow(() -> new BusinessException("Cererea de înscriere nu mai există"));
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
        if (!participant.isConfirmed()) {
            notificationService.withdrawRequest(player, tournament);
        }
    }

    /** Organizatorul înscrie un jucător, confirmat direct; dacă jucătorul ceruse deja înscrierea, cererea se confirmă. */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public TournamentParticipant addParticipant(Long tournamentId, Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new BusinessException("Jucătorul nu mai există"));
        Tournament tournament = requireTournament(tournamentId);
        Optional<TournamentParticipant> request = participantRepository.findByTournamentIdAndPlayerId(tournamentId, playerId)
                .filter(p -> !p.isConfirmed());
        if (request.isPresent() && tournament.isRegistrationOpen()) {
            confirmParticipant(tournamentId, request.get().getId());
            return request.get();
        }
        return register(tournament, player, ParticipantStatus.CONFIRMED);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public void removeParticipant(Long tournamentId, Long participantId) {
        requireOpenTournament(tournamentId);
        TournamentParticipant participant = participantRepository.findById(participantId)
                .filter(p -> p.getTournament().getId().equals(tournamentId))
                .orElseThrow(() -> new BusinessException("Participantul nu mai este înscris"));
        participantRepository.delete(participant);
    }

    private TournamentParticipant register(Tournament tournament, Player player, ParticipantStatus status) {
        if (!tournament.isRegistrationOpen()) {
            throw new BusinessException("Înscrierea la acest turneu este închisă");
        }
        if (participantRepository.existsByTournamentIdAndPlayerId(tournament.getId(), player.getId())) {
            throw new BusinessException("{0} este deja înscris la acest turneu", player.getDisplayName());
        }
        TournamentParticipant participant = new TournamentParticipant(tournament, player);
        participant.setStatus(status);
        return participantRepository.save(participant);
    }

    // ------------------------------------------------------------------
    // Începerea turneului
    // ------------------------------------------------------------------

    /**
     * Stabilește configurarea turneului (tip, seturi, turneu comercial), închide înscrierea,
     * formează grupa (jucătorii ordonați după rating, descrescător) și generează toate meciurile.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public void start(Long tournamentId, TournamentSettings settings) {
        Tournament tournament = requireOpenTournament(tournamentId);
        List<TournamentParticipant> participants = new ArrayList<>(participantRepository.findByTournamentIdWithPlayer(tournamentId));
        if (participants.size() < 2) {
            throw new BusinessException("Pentru a începe turneul sunt necesari cel puțin 2 participanți");
        }
        applySettings(settings, tournament, participants.size());
        // cererile rămase neconfirmate nu intră în turneu; jucătorii sunt anunțați
        for (TournamentParticipant request : participantRepository.findPendingByTournamentIdWithPlayer(tournamentId)) {
            participantRepository.delete(request);
            notificationService.notifyPlayer(request.getPlayer(), NotificationType.REGISTRATION_EXPIRED, tournament);
        }
        participants.sort(BY_RATING);
        for (int i = 0; i < participants.size(); i++) {
            TournamentParticipant participant = participants.get(i);
            participant.setSeed(i + 1);
            participant.setSeedRating(participant.getPlayer().getRating());
        }
        participantRepository.saveAll(participants);

        if (tournament.isGroupsFormat()) {
            createGroupStage(tournament, participants);
        } else {
            List<TournamentMatch> matches = new ArrayList<>();
            for (RoundRobinScheduler.Pairing pairing : RoundRobinScheduler.schedule(participants.size())) {
                matches.add(new TournamentMatch(tournament, pairing.round(),
                        participants.get(pairing.first()), participants.get(pairing.second())));
            }
            matchRepository.saveAll(matches);
        }

        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournament.setStartedAt(Instant.now());
        tournamentRepository.save(tournament);
    }

    /**
     * Etapa 1: grupele, cu jucătorii repartizați pe urne după rating și trași la sorți în fiecare urnă
     * ({@link GroupStagePlanner#draw}), și meciurile lor. În grupă jucătorii rămân ordonați după rating.
     */
    private void createGroupStage(Tournament tournament, List<TournamentParticipant> byRating) {
        List<List<Integer>> plan = GroupStagePlanner.draw(byRating.size(), tournament.getGroupCount(), RANDOM);
        List<TournamentMatch> matches = new ArrayList<>();
        for (int g = 0; g < plan.size(); g++) {
            TournamentGroup group = groupRepository.save(new TournamentGroup(tournament, TournamentStage.GROUPS,
                    g + 1, TournamentGroup.groupName(g + 1)));
            List<TournamentParticipant> members = plan.get(g).stream().map(byRating::get).toList();
            for (int i = 0; i < members.size(); i++) {
                memberRepository.save(new TournamentGroupMember(group, members.get(i), i + 1));
            }
            for (RoundRobinScheduler.Pairing pairing : RoundRobinScheduler.schedule(members.size())) {
                matches.add(new TournamentMatch(tournament, group, pairing.round(),
                        members.get(pairing.first()), members.get(pairing.second())));
            }
        }
        matchRepository.saveAll(matches);
        tournament.setStage(TournamentStage.GROUPS);
    }

    /**
     * Reîmparte grupele etapei 1 printr-o nouă tragere la sorți (doar administratorul), cât timp
     * nu s-a înregistrat niciun rezultat. Meciurile grupelor se generează din nou.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void redrawGroups(Long tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (!canRedrawGroups(tournament)) {
            throw new BusinessException("Grupele se pot reîmpărți doar în etapa grupelor");
        }
        List<TournamentMatch> matches = matchRepository.findByTournamentIdWithParticipants(tournamentId);
        if (matches.stream().anyMatch(TournamentMatch::isPlayed)) {
            throw new BusinessException("Grupele nu se mai pot reîmpărți: există deja rezultate înregistrate");
        }
        matchRepository.deleteAll(matches);
        matchRepository.flush();
        List<TournamentGroup> groups = groupRepository.findByTournamentIdOrderByStageAscPositionAsc(tournamentId);
        memberRepository.deleteAll(memberRepository.findByTournamentIdWithParticipants(tournamentId));
        memberRepository.flush();
        groupRepository.deleteAll(groups);
        groupRepository.flush();

        // ordinea după rating, stabilită la începerea turneului
        List<TournamentParticipant> bySeed = new ArrayList<>(participantRepository.findByTournamentIdWithPlayer(tournamentId));
        bySeed.sort(Comparator.comparing(TournamentParticipant::getSeed));
        createGroupStage(tournament, bySeed);
        tournamentRepository.save(tournament);
    }

    /** Grupele se pot reîmpărți: turneu „Grupe + finale” în etapa 1 (verificarea rezultatelor se face separat). */
    static boolean canRedrawGroups(Tournament tournament) {
        return tournament.isGroupsFormat() && tournament.getStatus() == TournamentStatus.IN_PROGRESS
                && tournament.getStage() == TournamentStage.GROUPS;
    }

    /**
     * Etapa 2 la „Grupe + finale”: primii {@code qualifiers} din fiecare grupă trec în Finala 1,
     * ceilalți în Finala 2. Ambele finale se joacă Round Robin; perechile care s-au întâlnit deja
     * în aceeași grupă nu mai joacă, iar rezultatul lor se preia în tabelul finalei.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public void startFinals(Long tournamentId, int qualifiers) {
        Tournament tournament = requireTournament(tournamentId);
        if (!tournament.isGroupsFormat() || tournament.getStatus() != TournamentStatus.IN_PROGRESS
                || tournament.getStage() != TournamentStage.GROUPS) {
            throw new BusinessException("Etapa 2 se poate începe doar după etapa grupelor");
        }
        if (tournament.isKnockoutFormat()) {
            throw new BusinessException("La acest turneu etapa 2 este un tablou eliminatoriu");
        }
        List<TournamentParticipant> participants = new ArrayList<>(participantRepository.findByTournamentIdWithPlayer(tournamentId));
        List<TournamentMatch> matches = matchRepository.findByTournamentIdWithParticipants(tournamentId);
        List<GroupView> groups = groupViews(tournament, matches, tiebreakRepository.findByTournamentIdWithParticipants(tournamentId));
        long unplayed = groups.stream().mapToLong(g -> g.matches().size() - g.playedMatches()).sum();
        if (unplayed > 0) {
            throw new BusinessException("Etapa 1 nu s-a terminat: mai sunt {0} meciuri fără rezultat", unplayed);
        }
        long tiebreaks = groups.stream().mapToLong(g -> g.pendingTiebreaks().size()).sum();
        if (tiebreaks > 0) {
            throw new BusinessException("Etapa 1 nu s-a terminat: mai sunt {0} seturi de departajare de jucat", tiebreaks);
        }
        int smallest = groups.stream().mapToInt(g -> g.members().size()).min().orElse(0);
        if (qualifiers < 1 || qualifiers >= smallest) {
            throw new BusinessException("Din fiecare grupă se pot califica între 1 și {0} jucători", smallest - 1);
        }

        // locul fiecărui jucător în grupa lui
        Map<Long, Integer> placeOf = new HashMap<>();
        Map<Long, Long> groupOf = new HashMap<>();
        for (GroupView g : groups) {
            g.standings().forEach(row -> placeOf.put(row.id(), row.place()));
            g.members().forEach(p -> groupOf.put(p.getId(), g.group().getId()));
        }
        // ordinea în finale: întâi toți câștigătorii de grupă, apoi locurile 2 …; la același loc, după rating
        Comparator<TournamentParticipant> order = Comparator
                .comparingInt((TournamentParticipant p) -> placeOf.get(p.getId()))
                .thenComparingInt(p -> -p.getSeedRating())
                .thenComparingInt(TournamentParticipant::getSeed);
        List<TournamentParticipant> final1 = participants.stream()
                .filter(p -> placeOf.get(p.getId()) <= qualifiers).sorted(order).toList();
        List<TournamentParticipant> final2 = participants.stream()
                .filter(p -> placeOf.get(p.getId()) > qualifiers).sorted(order).toList();

        List<TournamentMatch> newMatches = new ArrayList<>();
        newMatches.addAll(createFinal(tournament, TournamentGroup.FINAL_1, "Finala 1", final1, groupOf));
        newMatches.addAll(createFinal(tournament, TournamentGroup.FINAL_2, "Finala 2", final2, groupOf));
        matchRepository.saveAll(newMatches);

        tournament.setStage(TournamentStage.FINALS);
        tournament.setQualifiersPerGroup(qualifiers);
        tournamentRepository.saveAndFlush(tournament);
        if (newMatches.isEmpty()) {
            finish(tournament);
            ratingService.recalculateAll();
        }
    }

    /**
     * Etapa 2 la „Grupe + eliminare”: primii {@code qualifiers} din fiecare grupă intră în tabloul eliminatoriu,
     * așezați după locul din grupă (întâi câștigătorii de grupă), apoi după rating. Meciurile tabloului se creează
     * pe măsură ce se știu jucătorii lor.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public void startKnockout(Long tournamentId, int qualifiers, KnockoutType type) {
        Tournament tournament = requireTournament(tournamentId);
        if (!tournament.isKnockoutFormat() || tournament.getStatus() != TournamentStatus.IN_PROGRESS
                || tournament.getStage() != TournamentStage.GROUPS) {
            throw new BusinessException("Etapa 2 se poate începe doar după etapa grupelor");
        }
        if (type == null) {
            throw new BusinessException("Alegeți tipul tabloului eliminatoriu");
        }
        List<TournamentParticipant> participants = new ArrayList<>(participantRepository.findByTournamentIdWithPlayer(tournamentId));
        List<TournamentMatch> matches = matchRepository.findByTournamentIdWithParticipants(tournamentId);
        List<GroupView> groups = groupViews(tournament, matches, tiebreakRepository.findByTournamentIdWithParticipants(tournamentId));
        long unplayed = groups.stream().mapToLong(g -> g.matches().size() - g.playedMatches()).sum();
        if (unplayed > 0) {
            throw new BusinessException("Etapa 1 nu s-a terminat: mai sunt {0} meciuri fără rezultat", unplayed);
        }
        long tiebreaks = groups.stream().mapToLong(g -> g.pendingTiebreaks().size()).sum();
        if (tiebreaks > 0) {
            throw new BusinessException("Etapa 1 nu s-a terminat: mai sunt {0} seturi de departajare de jucat", tiebreaks);
        }
        int smallest = groups.stream().mapToInt(g -> g.members().size()).min().orElse(0);
        if (qualifiers < 1 || qualifiers > smallest) {
            throw new BusinessException("Din fiecare grupă se pot califica între 1 și {0} jucători", smallest);
        }
        Map<Long, Integer> placeOf = new HashMap<>();
        for (GroupView g : groups) {
            g.standings().forEach(row -> placeOf.put(row.id(), row.place()));
        }
        Comparator<TournamentParticipant> order = Comparator
                .comparingInt((TournamentParticipant p) -> placeOf.getOrDefault(p.getId(), Integer.MAX_VALUE))
                .thenComparingInt(p -> -p.getSeedRating())
                .thenComparingInt(TournamentParticipant::getSeed);
        List<TournamentParticipant> qualified = participants.stream()
                .filter(p -> placeOf.getOrDefault(p.getId(), Integer.MAX_VALUE) <= qualifiers)
                .sorted(order)
                .toList();
        if (qualified.size() < 2) {
            throw new BusinessException("În tabloul eliminatoriu sunt necesari cel puțin 2 jucători");
        }
        TournamentGroup bracket = groupRepository.save(
                new TournamentGroup(tournament, TournamentStage.KNOCKOUT, 1, "Tablou eliminatoriu"));
        for (int i = 0; i < qualified.size(); i++) {
            memberRepository.save(new TournamentGroupMember(bracket, qualified.get(i), i + 1));
        }
        tournament.setStage(TournamentStage.KNOCKOUT);
        tournament.setQualifiersPerGroup(qualifiers);
        tournament.setKnockoutType(type);
        tournamentRepository.saveAndFlush(tournament);
        syncBracket(tournament);
    }

    /**
     * Pune meciurile tabloului eliminatoriu în acord cu rezultatele: creează meciurile ai căror jucători s-au aflat
     * și le șterge pe cele care nu mai sunt valabile (după corectarea unui rezultat). Un meci următor care are deja
     * rezultat nu se șterge: corectura e refuzată.
     */
    private KnockoutView syncBracket(Tournament tournament) {
        KnockoutView view = knockoutView(tournament);
        if (view == null) {
            return null;
        }
        Map<String, long[]> playable = Bracket.playable(view.state());
        Map<String, TournamentMatch> kept = new HashMap<>();
        for (TournamentMatch row : view.matches().values()) {
            long[] pair = playable.get(row.getBracketSlot());
            boolean valid = pair != null && row.getParticipantA().getId() == pair[0] && row.getParticipantB().getId() == pair[1];
            if (valid) {
                kept.put(row.getBracketSlot(), row);
            } else if (row.isPlayed()) {
                throw new BusinessException("Rezultatul nu se mai poate schimba: meciurile următoare din tablou au deja rezultat. Ștergeți întâi rezultatele lor.");
            } else {
                matchRepository.delete(row);
            }
        }
        List<Bracket.Match> all = view.state().matches();
        for (int i = 0; i < all.size(); i++) {
            Bracket.Match m = all.get(i);
            if (m.playable() && !kept.containsKey(m.key())) {
                TournamentMatch match = new TournamentMatch(tournament, view.group(), i + 1,
                        view.participant(m.a()), view.participant(m.b()));
                match.setBracketSlot(m.key());
                matchRepository.save(match);
            }
        }
        matchRepository.flush();
        return view;
    }

    /** Un rezultat corectat într-un turneu încheiat a redeschis tabloul (de exemplu, e nevoie de meciul decisiv). */
    private void reopenIfBracketIncomplete(Tournament tournament, KnockoutView view) {
        if (view != null && !view.state().complete() && tournament.getStatus() == TournamentStatus.FINISHED) {
            tournament.setStatus(TournamentStatus.IN_PROGRESS);
            tournament.setFinishedAt(null);
            tournamentRepository.saveAndFlush(tournament);
            ratingService.recalculateAll();
        }
    }

    /** Tabloul eliminatoriu calculat din baza de date; {@code null} dacă turneul nu are (încă) tablou. */
    private KnockoutView knockoutView(Tournament tournament) {
        if (!tournament.isKnockoutFormat() || tournament.getKnockoutType() == null) {
            return null;
        }
        TournamentGroup group = groupRepository.findByTournamentIdOrderByStageAscPositionAsc(tournament.getId()).stream()
                .filter(TournamentGroup::isKnockout)
                .findFirst()
                .orElse(null);
        if (group == null) {
            return null;
        }
        List<TournamentParticipant> members = memberRepository.findByTournamentIdWithParticipants(tournament.getId()).stream()
                .filter(m -> m.getGroup().getId().equals(group.getId()))
                .sorted(Comparator.comparingInt(TournamentGroupMember::getSeed))
                .map(TournamentGroupMember::getParticipant)
                .toList();
        Map<String, TournamentMatch> rows = new java.util.LinkedHashMap<>();
        Map<String, Long> winners = new HashMap<>();
        for (TournamentMatch m : matchRepository.findByTournamentIdWithParticipants(tournament.getId())) {
            if (m.getGroup() != null && m.getGroup().getId().equals(group.getId()) && m.getBracketSlot() != null) {
                rows.put(m.getBracketSlot(), m);
                if (m.isPlayed()) {
                    winners.put(m.getBracketSlot(), m.getWinner().getId());
                }
            }
        }
        Bracket.State state = Bracket.compute(tournament.getKnockoutType(),
                members.stream().map(TournamentParticipant::getId).toList(), winners);
        return new KnockoutView(group, members, rows, state);
    }

    private List<TournamentMatch> createFinal(Tournament tournament, int position, String name,
                                              List<TournamentParticipant> members, Map<Long, Long> groupOf) {
        if (members.isEmpty()) {
            return List.of();
        }
        TournamentGroup group = groupRepository.save(new TournamentGroup(tournament, TournamentStage.FINALS, position, name));
        for (int i = 0; i < members.size(); i++) {
            memberRepository.save(new TournamentGroupMember(group, members.get(i), i + 1));
        }
        List<TournamentMatch> matches = new ArrayList<>();
        for (RoundRobinScheduler.Pairing pairing : GroupStagePlanner.scheduleWithout(members.size(),
                (a, b) -> groupOf.get(members.get(a).getId()).equals(groupOf.get(members.get(b).getId())))) {
            matches.add(new TournamentMatch(tournament, group, pairing.round(),
                    members.get(pairing.first()), members.get(pairing.second())));
        }
        return matches;
    }

    /**
     * Încheie manual un turneu în desfășurare (administrator sau manager de turnee). Meciurile fără rezultat
     * rămân nejucate și nu contează la rating; clasamentul și premiile se stabilesc din meciurile jucate.
     *
     * @return numărul meciurilor rămase fără rezultat
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public long finishManually(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new BusinessException("Turneul nu mai există"));
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            throw new BusinessException("Doar un turneu în desfășurare poate fi încheiat");
        }
        if (requiresFinalsBeforeFinish(tournament)) {
            throw new BusinessException("La un turneu comercial cu două etape premiile se acordă în finale: începeți etapa 2 înainte de încheiere");
        }
        long unplayed = matchRepository.countByTournamentIdAndOutcomeIsNull(tournamentId);
        finish(tournament);
        ratingService.recalculateAll();
        return unplayed;
    }

    /**
     * La un turneu comercial „Grupe + finale”, câștigătorul Finalei 2 primește obligatoriu cât taxa,
     * deci turneul se poate încheia doar după formarea finalelor.
     */
    static boolean requiresFinalsBeforeFinish(Tournament tournament) {
        return tournament.isGroupsFormat() && tournament.isCommercial()
                && tournament.getStage() == TournamentStage.GROUPS;
    }

    private void finish(Tournament tournament) {
        tournament.setStatus(TournamentStatus.FINISHED);
        tournament.setFinishedAt(Instant.now());
        tournamentRepository.saveAndFlush(tournament);
    }

    /** După începerea etapei 2, rezultatele din grupe nu se mai pot schimba (au decis calificarea). */
    private static void requireEditableStage(TournamentMatch match) {
        TournamentGroup group = match.getGroup();
        if (group != null && group.isGroupStage() && match.getTournament().getStage() != TournamentStage.GROUPS) {
            throw new BusinessException("Rezultatele etapei 1 nu mai pot fi modificate după începerea etapei 2");
        }
    }

    // ------------------------------------------------------------------
    // Rezultate
    // ------------------------------------------------------------------

    /**
     * Înregistrează (sau corectează) rezultatul unui meci. Permis administratorului, managerului de turnee și,
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
        requireEditableStage(match);

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
                throw new BusinessException("Scor invalid: câștigătorul trebuie să aibă {0} seturi, iar adversarul mai puține", setsToWin);
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
        // un rezultat nou schimbă egalitățile: seturile de departajare ale tabelului nu mai sunt valabile
        deleteTiebreaks(tournament.getId(), match.getGroup());
        if (match.getGroup() != null && match.getGroup().isKnockout()) {
            reopenIfBracketIncomplete(tournament, syncBracket(tournament));
        }

        finishIfComplete(tournament);
        // turneu încheiat acum sau rezultat corectat într-un turneu încheiat: ratingul se recalculează
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            ratingService.recalculateAll();
        }
        return match;
    }

    /**
     * Înregistrează (sau corectează) scorul unui set de departajare. Setul trebuie să fie unul cerut de tabel
     * (o pereche de jucători rămași la egalitate) sau unul deja jucat. La corectare, rundele de după el se șterg.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public TournamentTiebreak recordTiebreak(Long tournamentId, Long groupId, int round, long participantA,
                                             long participantB, int pointsA, int pointsB) {
        Tournament tournament = requireTournament(tournamentId);
        requireCanRecord(tournament);
        validateSetScore(pointsA, pointsB);
        TournamentGroup group = null;
        if (groupId != null) {
            group = groupRepository.findById(groupId)
                    .filter(g -> g.getTournament().getId().equals(tournamentId))
                    .orElseThrow(() -> new BusinessException("Grupa nu mai există"));
            if (group.isGroupStage() && tournament.getStage() != TournamentStage.GROUPS) {
                throw new BusinessException("Rezultatele etapei 1 nu mai pot fi modificate după începerea etapei 2");
            }
        }
        List<TournamentTiebreak> all = tiebreakRepository.findByTournamentIdWithParticipants(tournamentId);
        TournamentTiebreak tiebreak = all.stream()
                .filter(t -> java.util.Objects.equals(t.groupId(), groupId) && t.getRoundNo() == round
                        && t.between(participantA, participantB))
                .findFirst()
                .orElse(null);
        if (tiebreak == null) {
            boolean required = pendingTiebreaks(tournament, all).stream().anyMatch(p ->
                    java.util.Objects.equals(p.groupId(), groupId) && p.pairing().round() == round
                            && ((p.pairing().a() == participantA && p.pairing().b() == participantB)
                            || (p.pairing().a() == participantB && p.pairing().b() == participantA)));
            if (!required) {
                throw new BusinessException("Acest set de departajare nu mai este necesar");
            }
            tiebreak = new TournamentTiebreak(tournament, group, round,
                    participantRepository.getReferenceById(participantA), participantRepository.getReferenceById(participantB));
            tiebreak.setPoints(pointsA, pointsB);
        } else {
            boolean sameOrder = tiebreak.getParticipantA().getId() == participantA;
            tiebreak.setPoints(sameOrder ? pointsA : pointsB, sameOrder ? pointsB : pointsA);
            // rundele următoare au fost jucate după vechiul scor
            Long table = groupId;
            tiebreakRepository.deleteAll(all.stream()
                    .filter(t -> java.util.Objects.equals(t.groupId(), table) && t.getRoundNo() > round)
                    .toList());
        }
        tiebreak.setRecordedBy(SecurityUtils.currentUsername().orElse(null));
        tiebreak.setRecordedAt(Instant.now());
        tiebreakRepository.saveAndFlush(tiebreak);
        boolean wasFinished = tournament.getStatus() == TournamentStatus.FINISHED;
        finishIfComplete(tournament);
        if (!wasFinished && tournament.getStatus() == TournamentStatus.FINISHED) {
            // turneul s-a încheiat acum: meciurile lui intră în rating
            ratingService.recalculateAll();
        }
        return tiebreak;
    }

    /** Un set: câștigă primul la 11 puncte, cu cel puțin 2 puncte diferență (după 10:10, la diferență de 2). */
    static void validateSetScore(int a, int b) {
        int high = Math.max(a, b);
        int low = Math.min(a, b);
        boolean valid = low >= 0 && (high == 11 && low <= 9 || high > 11 && high - low == 2);
        if (!valid) {
            throw new BusinessException("Scor invalid pentru un set: câștigă primul la 11 puncte, cu 2 puncte diferență (ex. 11:7 sau 12:10)");
        }
    }

    /** Ultima etapă e completă (meciuri și seturi de departajare): turneul se încheie automat. */
    private void finishIfComplete(Tournament tournament) {
        boolean lastStage = !tournament.isGroupsFormat() || tournament.getStage() == TournamentStage.FINALS
                || tournament.getStage() == TournamentStage.KNOCKOUT;
        if (tournament.getStage() == TournamentStage.KNOCKOUT) {
            KnockoutView bracket = knockoutView(tournament);
            lastStage = bracket != null && bracket.state().complete();
        }
        if (tournament.getStatus() == TournamentStatus.IN_PROGRESS && lastStage
                && matchRepository.countByTournamentIdAndOutcomeIsNull(tournament.getId()) == 0
                && pendingTiebreaks(tournament, tiebreakRepository.findByTournamentIdWithParticipants(tournament.getId())).isEmpty()) {
            finish(tournament);
        }
    }

    /** Un set de departajare de jucat, cu grupa lui ({@code null} la Round Robin). */
    record PendingTiebreak(Long groupId, StandingsCalculator.Pairing pairing) {
    }

    private List<PendingTiebreak> pendingTiebreaks(Tournament tournament, List<TournamentTiebreak> tiebreaks) {
        List<TournamentMatch> matches = matchRepository.findByTournamentIdWithParticipants(tournament.getId());
        List<PendingTiebreak> pending = new ArrayList<>();
        if (tournament.isGroupsFormat()) {
            for (GroupView g : groupViews(tournament, matches, tiebreaks)) {
                g.pendingTiebreaks().forEach(p -> pending.add(new PendingTiebreak(g.group().getId(), p)));
            }
        } else {
            List<TournamentParticipant> participants = new ArrayList<>(participantRepository.findByTournamentIdWithPlayer(tournament.getId()));
            standings(tournament, participants, matches, tiebreaks).tiebreaks()
                    .forEach(p -> pending.add(new PendingTiebreak(null, p)));
        }
        return pending;
    }

    private void deleteTiebreaks(Long tournamentId, TournamentGroup group) {
        Long groupId = group != null ? group.getId() : null;
        tiebreakRepository.deleteAll(tiebreakRepository.findByTournamentIdWithParticipants(tournamentId).stream()
                .filter(t -> java.util.Objects.equals(t.groupId(), groupId))
                .toList());
        tiebreakRepository.flush();
    }

    /** Șterge rezultatul unui meci (administrator sau manager de turnee). Un turneu încheiat redevine „în desfășurare”. */
    @PreAuthorize("hasAnyRole('ADMIN', 'TOURNAMENT_MANAGER')")
    @Transactional
    public void clearResult(Long matchId) {
        TournamentMatch match = matchRepository.findByIdWithParticipants(matchId)
                .orElseThrow(() -> new BusinessException("Meciul nu mai există"));
        requireEditableStage(match);
        match.clearResult();
        matchRepository.saveAndFlush(match);
        deleteTiebreaks(match.getTournament().getId(), match.getGroup());
        Tournament tournament = match.getTournament();
        if (match.getGroup() != null && match.getGroup().isKnockout()) {
            syncBracket(tournament);
        }
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

        List<TournamentTiebreak> tiebreaks = tournament.isStarted()
                ? tiebreakRepository.findByTournamentIdWithParticipants(tournament.getId())
                : List.of();
        List<GroupView> groups = tournament.isStarted() && tournament.isGroupsFormat()
                ? groupViews(tournament, matches, tiebreaks)
                : List.of();
        StandingsCalculator.Table table = tournament.isStarted() && !tournament.isGroupsFormat()
                ? standings(tournament, participants, matches, tiebreaks)
                : new StandingsCalculator.Table(List.of(), List.of());
        List<StandingsCalculator.Row> standings = table.rows();
        KnockoutView knockout = tournament.getStage() == TournamentStage.KNOCKOUT ? knockoutView(tournament) : null;

        BigDecimal pool = null;
        List<PrizePlace> prizes = List.of();
        PrizeDistribution distribution = tournament.getPrizeDistribution();
        if (distribution != null && tournament.getEntryFee() != null) {
            pool = tournament.getEntryFee().multiply(BigDecimal.valueOf(participants.size()));
            if (tournament.isKnockoutFormat()) {
                prizes = knockoutPrizes(tournament, distribution, pool, knockout);
            } else if (tournament.isGroupsFormat()) {
                prizes = groupPrizes(tournament, distribution, pool, groups);
            } else {
                prizes = prizes(tournament, distribution, pool, participants, standings);
            }
        }

        Optional<Player> currentPlayer = currentPlayer();
        List<TournamentParticipant> pending = tournament.isRegistrationOpen()
                ? participantRepository.findPendingByTournamentIdWithPlayer(tournament.getId())
                : List.of();
        TournamentParticipant own = currentPlayer
                .flatMap(player -> java.util.stream.Stream.concat(participants.stream(), pending.stream())
                        .filter(p -> p.getPlayer().getId().equals(player.getId()))
                        .findFirst())
                .orElse(null);
        boolean manager = SecurityUtils.canManageTournaments();
        boolean canSelfRegister = tournament.isRegistrationOpen() && currentPlayer.isPresent() && own == null;
        boolean canRecord = (tournament.getStatus() == TournamentStatus.IN_PROGRESS && (manager || (own != null && own.isConfirmed())))
                || (tournament.getStatus() == TournamentStatus.FINISHED && manager);

        Map<Long, RatingHistory> ratingChanges = new HashMap<>();
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            historyRepository.findByTournamentId(tournament.getId())
                    .forEach(h -> ratingChanges.put(h.getPlayer().getId(), h));
        }

        return new TournamentDetails(tournament, participants, matches, standings, pool, prizes, own,
                manager, canSelfRegister, canRecord, ratingChanges, groups,
                tournament.isGroupsFormat() ? List.of() : tiebreaks, table.tiebreaks(),
                manager ? pending : List.of(), knockout);
    }

    /**
     * Premiile la „Grupe + eliminare”: suma acumulată se împarte (100% / 60-40% / 50-30-20%) după locurile din
     * tabloul eliminatoriu. La locurile împărțite (de exemplu locul 3 la sistemul olimpic) premiile se adună
     * și se împart egal.
     */
    private static List<PrizePlace> knockoutPrizes(Tournament tournament, PrizeDistribution distribution,
                                                   BigDecimal pool, KnockoutView knockout) {
        boolean finished = tournament.getStatus() == TournamentStatus.FINISHED && knockout != null;
        Map<Integer, List<String>> byPlace = new java.util.TreeMap<>();
        if (finished) {
            for (Bracket.Placing placing : knockout.state().placings()) {
                TournamentParticipant p = knockout.participant(placing.participantId());
                byPlace.computeIfAbsent(placing.place(), k -> new ArrayList<>()).add(p.getPlayer().getDisplayName());
            }
        }
        List<BigDecimal> amounts = distribution.split(pool);
        List<Integer> percentages = distribution.percentages();
        List<PrizePlace> prizes = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            int place = i + 1;
            List<String> winners = byPlace.entrySet().stream()
                    .filter(e -> place >= e.getKey() && place < e.getKey() + e.getValue().size())
                    .findFirst().map(Map.Entry::getValue).orElse(List.of());
            prizes.add(new PrizePlace(place, "Locul " + place, percentages.get(i), amounts.get(i),
                    winners.isEmpty() ? null : String.join(", ", winners), winners.size() > 1));
        }
        return prizes;
    }

    private static StandingsCalculator.Table standings(Tournament tournament,
                                                       List<TournamentParticipant> participants,
                                                       List<TournamentMatch> matches,
                                                       List<TournamentTiebreak> tiebreaks) {
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
        boolean allPlayed = matches.stream().allMatch(TournamentMatch::isPlayed);
        return StandingsCalculator.table(competitors, results, tournament.getSetsToWin(),
                tournament.isCommercial() && allPlayed, tiebreakResults(tiebreaks, null));
    }

    private static List<StandingsCalculator.Tiebreak> tiebreakResults(List<TournamentTiebreak> tiebreaks, Long groupId) {
        return tiebreaks.stream()
                .filter(t -> java.util.Objects.equals(t.groupId(), groupId))
                .map(t -> new StandingsCalculator.Tiebreak(t.getRoundNo(), t.getParticipantA().getId(),
                        t.getParticipantB().getId(), t.getPointsA(), t.getPointsB()))
                .toList();
    }

    private static List<PrizePlace> prizes(Tournament tournament, PrizeDistribution distribution, BigDecimal pool,
                                           List<TournamentParticipant> participants,
                                           List<StandingsCalculator.Row> standings) {
        boolean finished = tournament.getStatus() == TournamentStatus.FINISHED;
        Map<Long, String> names = new HashMap<>();
        participants.forEach(p -> names.put(p.getId(), p.getPlayer().getDisplayName()));
        List<BigDecimal> amounts = distribution.split(pool);
        List<Integer> percentages = distribution.percentages();
        List<PrizePlace> prizes = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            List<String> winners = finished ? namesCovering(standings, names, i + 1) : List.of();
            prizes.add(new PrizePlace(i + 1, "Locul " + (i + 1), percentages.get(i), amounts.get(i),
                    winners.isEmpty() ? null : String.join(", ", winners), winners.size() > 1));
        }
        return prizes;
    }

    /**
     * Jucătorii care ocupă locul dat. La o egalitate nedepartajată, jucătorii împart locurile ocupate
     * împreună: trei jucători pe locul 1 ocupă locurile 1, 2 și 3.
     */
    static List<String> namesCovering(List<StandingsCalculator.Row> rows, Map<Long, String> names, int place) {
        Map<Integer, List<StandingsCalculator.Row>> byPlace = new HashMap<>();
        rows.stream().filter(r -> r.place() > 0)
                .forEach(r -> byPlace.computeIfAbsent(r.place(), k -> new ArrayList<>()).add(r));
        for (Map.Entry<Integer, List<StandingsCalculator.Row>> e : byPlace.entrySet()) {
            int first = e.getKey();
            int last = first + e.getValue().size() - 1;
            if (place >= first && place <= last) {
                return e.getValue().stream().map(r -> names.get(r.id())).toList();
            }
        }
        return List.of();
    }

    /** Grupele (etapa 1) și finalele (etapa 2), cu tabelele lor. */
    private List<GroupView> groupViews(Tournament tournament, List<TournamentMatch> matches,
                                       List<TournamentTiebreak> tiebreaks) {
        List<TournamentGroup> groups = new ArrayList<>(groupRepository.findByTournamentIdOrderByStageAscPositionAsc(tournament.getId()));
        groups.removeIf(TournamentGroup::isKnockout);
        groups.sort(Comparator.comparing((TournamentGroup g) -> g.getStage().ordinal())
                .thenComparingInt(TournamentGroup::getPosition));
        Map<Long, List<TournamentGroupMember>> membersByGroup = new HashMap<>();
        for (TournamentGroupMember m : memberRepository.findByTournamentIdWithParticipants(tournament.getId())) {
            membersByGroup.computeIfAbsent(m.getGroup().getId(), k -> new ArrayList<>()).add(m);
        }

        List<GroupView> views = new ArrayList<>();
        for (TournamentGroup group : groups) {
            List<TournamentParticipant> members = membersByGroup.getOrDefault(group.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(TournamentGroupMember::getSeed))
                    .map(TournamentGroupMember::getParticipant)
                    .toList();
            Set<Long> ids = new HashSet<>();
            members.forEach(p -> ids.add(p.getId()));

            List<TournamentMatch> own = matches.stream()
                    .filter(m -> m.getGroup() != null && m.getGroup().getId().equals(group.getId()))
                    .toList();
            List<TournamentMatch> carried = group.isFinal()
                    ? matches.stream()
                        .filter(m -> m.getGroup() != null && !m.getGroup().isFinal())
                        .filter(m -> ids.contains(m.getParticipantA().getId()) && ids.contains(m.getParticipantB().getId()))
                        .toList()
                    : List.of();

            List<StandingsCalculator.Competitor> competitors = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                competitors.add(new StandingsCalculator.Competitor(members.get(i).getId(), i + 1));
            }
            List<TournamentMatch> counted = new ArrayList<>(own);
            counted.addAll(carried);
            boolean allPlayed = own.stream().allMatch(TournamentMatch::isPlayed);
            StandingsCalculator.Table table = StandingsCalculator.table(competitors, results(counted),
                    tournament.getSetsToWin(), tournament.isCommercial() && allPlayed,
                    tiebreakResults(tiebreaks, group.getId()));
            views.add(new GroupView(group, members, own, carried, table.rows(),
                    tiebreaks.stream().filter(t -> group.getId().equals(t.groupId())).toList(),
                    table.tiebreaks()));
        }
        return views;
    }

    private static List<StandingsCalculator.Result> results(List<TournamentMatch> matches) {
        return matches.stream()
                .filter(TournamentMatch::isPlayed)
                .map(m -> new StandingsCalculator.Result(
                        m.getParticipantA().getId(), m.getParticipantB().getId(),
                        m.getSetsA() != null ? m.getSetsA() : 0, m.getSetsB() != null ? m.getSetsB() : 0,
                        m.getWinner().getId(), m.isWalkover()))
                .toList();
    }

    /**
     * Premiile la „Grupe + finale”: câștigătorul Finalei 2 primește cât taxa de participare,
     * restul sumei se împarte între premiații Finalei 1 (100% / 60-40% / 50-30-20%).
     */
    private static List<PrizePlace> groupPrizes(Tournament tournament, PrizeDistribution distribution,
                                                BigDecimal pool, List<GroupView> groups) {
        boolean finished = tournament.getStatus() == TournamentStatus.FINISHED;
        GroupView final1 = groups.stream().filter(g -> g.group().isFinal()
                && g.group().getPosition() == TournamentGroup.FINAL_1).findFirst().orElse(null);
        GroupView final2 = groups.stream().filter(g -> g.group().isFinal()
                && g.group().getPosition() == TournamentGroup.FINAL_2).findFirst().orElse(null);

        List<PrizePlace> prizes = new ArrayList<>();
        BigDecimal fee = tournament.getEntryFee();
        BigDecimal rest = pool.subtract(fee).max(BigDecimal.ZERO);
        List<BigDecimal> amounts = distribution.split(rest);
        List<Integer> percentages = distribution.percentages();
        for (int i = 0; i < amounts.size(); i++) {
            List<String> winners = finished ? namesAtPlace(final1, i + 1) : List.of();
            prizes.add(new PrizePlace(i + 1, "Finala 1 · locul " + (i + 1), percentages.get(i), amounts.get(i),
                    winners.isEmpty() ? null : String.join(", ", winners), winners.size() > 1));
        }
        List<String> final2Winners = finished ? namesAtPlace(final2, 1) : List.of();
        prizes.add(new PrizePlace(1, "Finala 2 · locul 1", null, fee.setScale(2, RoundingMode.HALF_UP),
                final2Winners.isEmpty() ? null : String.join(", ", final2Winners), final2Winners.size() > 1));
        return prizes;
    }

    /**
     * Jucătorii de pe locul dat al unei finale. Dacă finala s-a încheiat (manual) fără niciun meci jucat,
     * contează ordinea de calificare din grupe (locul în grupă, apoi ratingul), deci premiile au mereu câștigător.
     */
    private static List<String> namesAtPlace(GroupView group, int place) {
        if (group == null) {
            return List.of();
        }
        boolean noStandings = group.standings().stream().allMatch(r -> r.place() == 0);
        if (noStandings) {
            return place <= group.members().size()
                    ? List.of(group.members().get(place - 1).getPlayer().getDisplayName())
                    : List.of();
        }
        Map<Long, String> names = new HashMap<>();
        group.members().forEach(p -> names.put(p.getId(), p.getPlayer().getDisplayName()));
        return namesCovering(group.standings(), names, place);
    }

    private void requireCanRecord(Tournament tournament) {
        if (tournament.getStatus() == TournamentStatus.REGISTRATION) {
            throw new BusinessException("Turneul nu a început încă");
        }
        if (SecurityUtils.canManageTournaments()) {
            return;
        }
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            throw new BusinessException("Turneul s-a încheiat; rezultatele pot fi corectate doar de administrator sau de managerul de turnee");
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
    private static void applySettings(TournamentSettings settings, Tournament tournament, int participants) {
        if (settings == null || settings.getFormat() == null) {
            throw new BusinessException("Alegeți tipul turneului");
        }
        if (settings.getBestOf() == null || !ALLOWED_BEST_OF.contains(settings.getBestOf())) {
            throw new BusinessException("Numărul de seturi trebuie să fie 3, 5 sau 7");
        }
        Integer groupCount = null;
        if (settings.getFormat() == TournamentFormat.GROUPS_FINALS || settings.getFormat() == TournamentFormat.GROUPS_KNOCKOUT) {
            groupCount = settings.getGroupCount();
            int minimum = settings.getFormat() == TournamentFormat.GROUPS_KNOCKOUT ? 1 : 2;
            if (groupCount == null || groupCount < minimum) {
                throw new BusinessException(minimum == 1 ? "Alegeți cel puțin o grupă" : "Alegeți cel puțin 2 grupe");
            }
            if (participants < groupCount * 2) {
                throw new BusinessException("Pentru {0} grupe sunt necesari cel puțin {1} participanți (minimum 2 în fiecare grupă)", groupCount, groupCount * 2);
            }
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
        tournament.setGroupCount(groupCount);
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

    private static final RandomGenerator RANDOM = new SecureRandom();

    /** Ordinea inițială: rating descrescător, apoi alfabetic. */
    private static final Comparator<TournamentParticipant> BY_RATING = Comparator
            .comparingInt((TournamentParticipant p) -> -p.getPlayer().getRating())
            .thenComparing(p -> p.getPlayer().getLastName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(p -> p.getPlayer().getFirstName(), String.CASE_INSENSITIVE_ORDER);
}
