package md.ttm.config;

import md.ttm.model.player.PlayHand;
import md.ttm.model.player.PlayStyle;
import md.ttm.model.player.Player;
import md.ttm.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profilul "seed": 10 jucători de test pentru prima pornire în producție, fără conturi,
 * fără telefoane și fără turnee. Se adaugă doar într-o bază fără niciun jucător
 * (rulează înaintea creării administratorului), deci profilul poate rămâne activ fără efecte.
 * Jucătorii se pot șterge oricând din clasament, cât timp nu au jucat într-un turneu.
 */
@Component
@Profile("seed")
@Order(0)
public class SeedPlayersInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedPlayersInitializer.class);

    private final PlayerRepository playerRepository;

    public SeedPlayersInitializer(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (playerRepository.count() > 0) {
            return;
        }
        save("Alexandru", "Munteanu", PlayStyle.ATTACK, PlayHand.RIGHT, "Chișinău", 1450, "Butterfly Viscaria", "Tenergy 05", "Dignics 09C");
        save("Vasile", "Gheorghiu", PlayStyle.DEFENCE, PlayHand.RIGHT, "Bălți", 1400, "Butterfly Defence Alpha", "Tenergy 64", "Feint Long III");
        save("Mariana", "Popa", PlayStyle.ATTACK, PlayHand.LEFT, "Chișinău", 1350, "Stiga Cybershape Carbon", "DNA Dragon Grip", "DNA Platinum XH");
        save("Nicolae", "Rotaru", PlayStyle.ATTACK, PlayHand.RIGHT, "Orhei", 1300, "DHS Hurricane Long 5", "Hurricane 3 Neo", "Tenergy 05");
        save("Tatiana", "Ursu", PlayStyle.DEFENCE, PlayHand.RIGHT, "Cahul", 1250, "Tibhar Defense Plus", "Evolution MX-P", "Grass D.TecS");
        save("Eugen", "Vrabie", PlayStyle.ATTACK, PlayHand.LEFT, "Chișinău", 1200, "Yasaka Ma Lin Extra Offensive", "Rakza 7", "Rakza 7 Soft");
        save("Pavel", "Botnaru", PlayStyle.DEFENCE, PlayHand.RIGHT, "Ungheni", 1150, null, null, null);
        save("Irina", "Cebotari", PlayStyle.ATTACK, PlayHand.RIGHT, "Soroca", 1100, "Butterfly Timo Boll ALC", "Tenergy 80", "Tenergy 80");
        save("Grigore", "Lupu", PlayStyle.ATTACK, PlayHand.RIGHT, "Comrat", 1050, null, null, null);
        save("Doina", "Rusnac", PlayStyle.DEFENCE, PlayHand.LEFT, "Chișinău", 1000, "Donic Defplay Senso", "Bluefire M2", null);
        log.info("Profilul seed: 10 jucători de test adăugați");
    }

    private void save(String firstName, String lastName, PlayStyle style, PlayHand hand, String city, int rating,
                      String blade, String forehand, String backhand) {
        Player player = new Player(firstName, lastName);
        player.setPlayStyle(style);
        player.setPlayHand(hand);
        player.setCity(city);
        player.setInitialRating(rating);
        player.setRating(rating);
        player.setBlade(blade);
        player.setForehandRubber(forehand);
        player.setBackhandRubber(backhand);
        playerRepository.save(player);
    }
}
