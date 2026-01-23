package utils.protocols.core;

/**
 * Exception thrown when a protocol encounters an error.
 */
public class ProtocolException extends RuntimeException {
    
    public ProtocolException(String message) {
        super(message);
    }
    
    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}

