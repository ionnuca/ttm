package md.ttm.service.tournament;

import md.ttm.model.tournament.Tournament;

/**
 * Un turneu din listă, cu numărul de participanți confirmați și, pentru organizatori,
 * numărul cererilor de înscriere neconfirmate.
 */
public record TournamentSummary(Tournament tournament, long participants, long pendingRequests) {
}
