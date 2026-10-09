package md.ttm.model.tournament;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Împărțirea sumei acumulate între câștigătorii unui turneu comercial.
 */
public enum PrizeDistribution {
    ONE_WINNER(100),
    TWO_WINNERS(60, 40),
    THREE_WINNERS(50, 30, 20);

    private final int[] percentages;

    PrizeDistribution(int... percentages) {
        this.percentages = percentages;
    }

    public static PrizeDistribution forWinners(int winners) {
        for (PrizeDistribution distribution : values()) {
            if (distribution.winners() == winners) {
                return distribution;
            }
        }
        throw new IllegalArgumentException("Număr de câștigători nesuportat: " + winners);
    }

    public int winners() {
        return percentages.length;
    }

    public List<Integer> percentages() {
        return Arrays.stream(percentages).boxed().toList();
    }

    /** De exemplu „2 câștigători (60% / 40%)”. */
    public String getLabel() {
        String split = Arrays.stream(percentages).mapToObj(p -> p + "%").collect(Collectors.joining(" / "));
        return winners() == 1 ? "1 câștigător (" + split + ")" : winners() + " câștigători (" + split + ")";
    }

    /**
     * Împarte suma pe locuri, rotunjit la bani; diferența de rotunjire merge la locul 1,
     * astfel încât totalul premiilor să fie exact suma acumulată.
     */
    public List<BigDecimal> split(BigDecimal pool) {
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal distributed = BigDecimal.ZERO;
        for (int percentage : percentages) {
            BigDecimal amount = pool.multiply(BigDecimal.valueOf(percentage))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
            amounts.add(amount);
            distributed = distributed.add(amount);
        }
        amounts.set(0, amounts.get(0).add(pool.setScale(2, RoundingMode.HALF_UP).subtract(distributed)));
        return amounts;
    }
}
