package md.ttm.config;

import md.ttm.service.rating.RatingService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * La pornire recalculează ratingurile, ca ele să fie mereu în acord cu turneele încheiate
 * (de exemplu după o actualizare a aplicației sau o modificare directă în baza de date).
 */
@Component
@Order(3)
public class RatingInitializer implements ApplicationRunner {

    private final RatingService ratingService;

    public RatingInitializer(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ratingService.recalculateAll();
    }
}
