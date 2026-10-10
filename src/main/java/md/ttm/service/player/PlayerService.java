package md.ttm.service.player;

import md.ttm.common.BusinessException;
import md.ttm.common.Texts;
import md.ttm.model.player.PlayStyle;
import md.ttm.model.player.Player;
import md.ttm.model.user.AppUser;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.PlayerRepository;
import md.ttm.security.SecurityUtils;
import md.ttm.service.rating.RatingService;
import md.ttm.service.tournament.TournamentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Operațiile cu jucători. Citirea e publică; modificările sunt permise doar
 * administratorului, cu excepția propriului profil, pe care îl poate edita orice utilizator logat.
 */
@Service
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final AppUserRepository userRepository;
    private final TournamentService tournamentService;
    private final RatingService ratingService;

    public PlayerService(PlayerRepository playerRepository, AppUserRepository userRepository,
                         TournamentService tournamentService, RatingService ratingService) {
        this.ratingService = ratingService;
        this.tournamentService = tournamentService;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
    }

    /** Clasamentul: toți jucătorii, ordonați după rating, cu locul fiecăruia. */
    public List<RankedPlayer> findRanked() {
        List<Player> players = playerRepository.findAllByOrderByRatingDescLastNameAscFirstNameAsc();
        List<RankedPlayer> result = new ArrayList<>(players.size());
        int rank = 0;
        Integer previousRating = null;
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (previousRating == null || player.getRating() != previousRating) {
                rank = i + 1;
                previousRating = player.getRating();
            }
            result.add(new RankedPlayer(rank, player));
        }
        return result;
    }

    public Optional<Player> findById(Long id) {
        return playerRepository.findById(id);
    }

    /**
     * Creează sau actualizează un jucător (administrator). Ratingul curent și statisticile
     * nu se editează direct: se recalculează din ratingul inițial și din turneele încheiate.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Player save(Player player) {
        normalize(player);
        if (player.getId() == null) {
            player.setRating(player.getInitialRating());
        }
        Player saved = playerRepository.saveAndFlush(player);
        ratingService.recalculateAll();
        return saved;
    }

    /** Șterge un jucător și contul de utilizator asociat, dacă există (administrator). */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new BusinessException("Jucătorul nu mai există"));
        Optional<AppUser> account = userRepository.findByPlayerId(playerId);
        if (account.isPresent()) {
            boolean ownAccount = SecurityUtils.currentUsername()
                    .map(account.get().getUsername()::equals)
                    .orElse(false);
            if (ownAccount) {
                throw new BusinessException("Nu vă puteți șterge propriul profil");
            }
        }
        tournamentService.releasePlayer(playerId);
        if (account.isPresent()) {
            userRepository.delete(account.get());
            userRepository.flush();
        }
        playerRepository.delete(player);
    }

    /**
     * Actualizează profilul de jucător al utilizatorului autentificat.
     * Se preiau datele personale și echipamentul. Ratingul și statisticile nu pot fi modificate de jucător;
     * administratorul își poate stabili ratingul inițial, iar ratingul curent se recalculează.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Player updateOwnProfile(Player edited) {
        String username = SecurityUtils.currentUsername()
                .orElseThrow(() -> new BusinessException("Nu sunteți autentificat"));
        Player own = userRepository.findByUsername(username)
                .map(AppUser::getPlayer)
                .orElseThrow(() -> new BusinessException("Contul nu are un profil de jucător"));
        if (edited.getId() != null && !edited.getId().equals(own.getId())) {
            throw new BusinessException("Puteți modifica doar propriul profil");
        }
        own.setFirstName(edited.getFirstName());
        own.setLastName(edited.getLastName());
        own.setPlayStyle(edited.getPlayStyle());
        own.setCity(edited.getCity());
        own.setPhone(edited.getPhone());
        own.setPlayHand(edited.getPlayHand());
        own.setBlade(edited.getBlade());
        own.setForehandRubber(edited.getForehandRubber());
        own.setBackhandRubber(edited.getBackhandRubber());
        normalize(own);
        boolean initialRatingChanged = SecurityUtils.isAdmin()
                && edited.getInitialRating() != own.getInitialRating();
        if (initialRatingChanged) {
            if (edited.getInitialRating() < 0) {
                throw new BusinessException("Ratingul inițial nu poate fi negativ");
            }
            own.setInitialRating(edited.getInitialRating());
        }
        Player saved = playerRepository.saveAndFlush(own);
        if (initialRatingChanged) {
            ratingService.recalculateAll();
            return playerRepository.findById(saved.getId()).orElseThrow();
        }
        return saved;
    }

    private static void normalize(Player player) {
        player.setFirstName(Texts.trimToNull(player.getFirstName()));
        player.setLastName(Texts.trimToNull(player.getLastName()));
        player.setCity(Texts.trimToNull(player.getCity()));
        player.setPhone(Texts.trimToNull(player.getPhone()));
        player.setBlade(Texts.trimToNull(player.getBlade()));
        player.setForehandRubber(Texts.trimToNull(player.getForehandRubber()));
        player.setBackhandRubber(Texts.trimToNull(player.getBackhandRubber()));
        if (player.getFirstName() == null || player.getLastName() == null) {
            throw new BusinessException("Numele și prenumele sunt obligatorii");
        }
        if (player.getPlayStyle() == null) {
            player.setPlayStyle(PlayStyle.ATTACK);
        }
    }
}
