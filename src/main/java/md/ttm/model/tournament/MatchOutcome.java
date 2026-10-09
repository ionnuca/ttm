package md.ttm.model.tournament;

/**
 * Felul în care s-a decis un meci.
 */
public enum MatchOutcome {
    /** Meci jucat; rezultatul e dat de seturi (ex. 3:1). */
    NORMAL,
    /** Victorie tehnică (W): adversarul a refuzat jocul. */
    WALKOVER
}
