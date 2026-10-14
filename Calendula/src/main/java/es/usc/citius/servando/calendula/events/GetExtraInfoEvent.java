package es.usc.citius.servando.calendula.events;

public class GetExtraInfoEvent {

    private final Status status;
    private final String url;
    private final Long patientId;
    private final int jobId;

    public GetExtraInfoEvent(final Status status, String url) {
        this(status, url, null, -1);
    }

    public GetExtraInfoEvent(final Status status, String url, Long patientId) {
        this(status, url, patientId, -1);
    }

    public GetExtraInfoEvent(final Status status, String url, Long patientId, int jobId) {
        this.status = status;
        this.url = url;
        this.patientId = patientId;
        this.jobId = jobId;
    }

    /** Exact asynchronous request identity, or -1 for legacy status events. */
    public int getJobId() {
        return jobId;
    }

    /**
     * Persistent owner ID of the PDF request. A null ID never authorizes
     * showing patient-specific provider content.
     */
    public Long getPatientId() {
        return patientId;
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
