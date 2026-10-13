package md.ttm.service.tournament;

import java.math.BigDecimal;

/**
 * Un premiu într-un turneu comercial.
 *
 * @param place      locul premiat (în Finala 1, la „Grupe + finale”)
 * @param title      de exemplu „Locul 1” sau „Finala 2 · locul 1”
 * @param percentage procentul din suma împărțită; {@code null} pentru premiul fix al Finalei 2
 * @param winnerName numele câștigătorului, cunoscut doar după încheierea turneului; la un loc împărțit,
 *                   numele tuturor jucătorilor de pe el, despărțite prin virgulă
 * @param shared     locul e împărțit (egalitate nedepartajată): premiile locurilor ocupate împreună
 *                   se adună și se împart egal
 */
public record PrizePlace(int place, String title, Integer percentage, BigDecimal amount, String winnerName,
                         boolean shared) {
}
