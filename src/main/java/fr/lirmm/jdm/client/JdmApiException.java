package fr.lirmm.jdm.client;

/**
 * Exception thrown when JDM API operations fail.
 */
public class JdmApiException extends Exception {

  /**
   * Creates a new JdmApiException with the specified message.
   *
   * @param message the error message
   */
  public JdmApiException(String message) {
    super(message);
  }

  /**
   * Creates a new JdmApiException with the specified message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public JdmApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
