package md.ttm.model.tournament;

import md.ttm.i18n.Tr;
/**
 * Etapele unui turneu.
 */
public enum TournamentStatus {
    /** Turneul e creat; jucătorii se pot înscrie. */
    REGISTRATION(Tr.key("Înscriere deschisă")),
    /** Turneul a început: grupa și meciurile sunt generate, se introduc rezultatele. */
    IN_PROGRESS(Tr.key("În desfășurare")),
    /** Toate meciurile au rezultat. */
    FINISHED(Tr.key("Încheiat"));

    private final String label;

    TournamentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Tr.t(label);
    }
}
