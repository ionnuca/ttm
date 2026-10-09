package md.ttm.service.tournament;

import java.math.BigDecimal;

/**
 * Premiul pentru un loc într-un turneu comercial.
 *
 * @param winnerName numele câștigătorului, cunoscut doar după încheierea turneului
 */
public record PrizePlace(int place, int percentage, BigDecimal amount, String winnerName) {
}
