package md.ttm.model.notification;

/** Felul notificării; textul afișat se alege după tip (și se traduce). */
public enum NotificationType {
    /** Pentru organizatori: un jucător a cerut înscrierea la un turneu. */
    REGISTRATION_REQUESTED,
    /** Pentru jucător: înscrierea a fost confirmată. */
    REGISTRATION_CONFIRMED,
    /** Pentru jucător: înscrierea a fost respinsă. */
    REGISTRATION_REJECTED,
    /** Pentru jucător: turneul a început fără ca înscrierea să fie confirmată. */
    REGISTRATION_EXPIRED
}
