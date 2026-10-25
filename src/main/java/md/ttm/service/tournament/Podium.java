package md.ttm.service.tournament;

/**
 * Locurile fruntașe ale unui jucător în turneele încheiate: de câte ori a fost pe locul 1, 2 și 3
 * (la „Grupe + finale”, locurile din Finala 1) și de câte ori a câștigat Finala 2.
 */
public record Podium(int first, int second, int third, int final2) {

    public static final Podium NONE = new Podium(0, 0, 0, 0);
}
