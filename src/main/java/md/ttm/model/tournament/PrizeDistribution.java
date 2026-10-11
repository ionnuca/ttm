package md.ttm.model.tournament;

import md.ttm.i18n.Tr;
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
        return winners() == 1 ? Tr.t("1 câștigător ({0})", split) : Tr.t("{0} câștigători ({1})", winners(), split);
    }

    /** Premiile se rotunjesc la multipli de 50 de lei. */
    public static final BigDecimal ROUNDING_STEP = new BigDecimal("50");

    /**
     * Împarte suma pe locuri după procente; fiecare premiu se rotunjește la cel mai apropiat
     * multiplu de {@link #ROUNDING_STEP} (25 se rotunjește în sus). Totalul premiilor poate diferi
     * ușor de suma acumulată.
     */
    public List<BigDecimal> split(BigDecimal pool) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (int percentage : percentages) {
            BigDecimal exact = pool.multiply(BigDecimal.valueOf(percentage)).divide(BigDecimal.valueOf(100));
            amounts.add(roundToStep(exact));
        }
        return amounts;
    }

    static BigDecimal roundToStep(BigDecimal amount) {
        return amount.divide(ROUNDING_STEP, 0, RoundingMode.HALF_UP).multiply(ROUNDING_STEP).setScale(2);
    }
}
