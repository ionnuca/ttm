package md.ttm.service.tournament;

import md.ttm.model.tournament.Tournament;

/**
 * Un turneu din listă, cu numărul de participanți.
 */
public record TournamentSummary(Tournament tournament, long participants) {
}
