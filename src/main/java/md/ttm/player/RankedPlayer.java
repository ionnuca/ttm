package md.ttm.player;

/**
 * Un jucător împreună cu locul lui în clasamentul după rating.
 * Jucătorii cu același rating au același loc (1, 2, 2, 4 ...).
 */
public record RankedPlayer(int rank, Player player) {
}
