package axitask.axi_crud.exceptions;

import lombok.Getter;


@Getter
public class LogicException extends RuntimeException {
    private final String errorCode;
    private final String errorDescription;

    public LogicException(String errorCode, String errorDescription) {
        super(errorDescription);
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public LogicException(String errorCode, String errorDescription, Throwable cause) {
        super(errorDescription, cause);
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }
}
