package md.ttm.model.tournament;

/** Starea înscrierii la turneu. */
public enum ParticipantStatus {
    /** Cerere de înscriere făcută de jucător, în așteptarea confirmării unui organizator. */
    PENDING,
    /** Participant confirmat (sau adăugat direct de organizator). */
    CONFIRMED
}
