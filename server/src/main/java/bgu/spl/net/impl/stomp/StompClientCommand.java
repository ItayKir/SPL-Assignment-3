package bgu.spl.net.impl.stomp;

public enum StompClientCommand {
    CONNECT("accept-version", "host", "login", "passcode"),
    SEND("destination"),
    SUBSCRIBE("destination", "id"),
    UNSUBSCRIBE("id"),
    DISCONNECT("receipt"),
    UNKNOWN();

    private final String[] requiredHeaders;

    StompClientCommand(String... requiredHeaders) {
        this.requiredHeaders = requiredHeaders;
    }

    public String[] getRequiredHeaders() {
        return requiredHeaders;
    }

    /**
     * Returns true iff all required headers exist
     * @param frame
     * @return boolean
     */
    public boolean validate(StompFrameParser frame) {
        for (String header : requiredHeaders) {
            if (!frame.hasHeader(header)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a missing required header for the StompFrameParser. If all required headers exist - returns null.
     * @param frame
     * @return String represeting the missing header.
     */
    public String getMissingHeader(StompFrameParser frame){
        for (String header : requiredHeaders) {
            if (!frame.hasHeader(header)) {
                return header;
            }
        }
        return null;
    }
}

// Client frames:
//CONNECT
//SEND
//SUBSCRIBE
//UNSUBSCRIBE
//DISCONNECT