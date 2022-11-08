package es.usc.citius.servando.calendula.events;

public class GetExtraInfoEvent {

    private final Status status;
    private final String url;

    public GetExtraInfoEvent(final Status status, String url) {
        this.status = status;
        this.url = url;
    }

    public Status getStatus() {
        return status;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Status of the Extra info request
     */
    public enum Status {
        /**
         * The update has just started
         */
        REQUEST_START,
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
