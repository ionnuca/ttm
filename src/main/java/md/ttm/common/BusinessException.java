package md.ttm.common;

/**
 * Eroare de business cu mesaj destinat direct utilizatorului (în română).
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
