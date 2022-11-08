package es.usc.citius.servando.calendula.events;


public class ActiveMedUpdateEvent {

    private final Status status;

    public ActiveMedUpdateEvent(final Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

    /**
     * Status of the med update
     */
    public enum Status {
        /**
         * The update has just started
         */
        UPDATE_START,
        /**
         * Update successful
         */
        SUCCESS,
        /**
         * Update failed because of no connection
         */
        ERROR_NO_CONNECTION,
        /**
         * Update failed because of generic authorization error
         */
        ERROR_AUTHORIZATION,
        /**
         * Update failed because of wrong authorization level
         */
        ERROR_AUTHORIZATION_LEVEL,
        /**
         * Update failed because of generic errors
         */
        ERROR_GENERIC,
        /**
         * Update failed because no valid user/db are present
         */
        ERROR_NO_USER_OR_DB
    }

}
