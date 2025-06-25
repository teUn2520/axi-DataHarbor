package axitask.axi_crud.exceptions;

public class JsonProcessingException extends LogicException {

    public JsonProcessingException(String errorCode, String errorDescription) {
        super(errorCode, errorDescription);
    }

    public JsonProcessingException(String errorCode, String errorDescription, Throwable cause) {
        super(errorCode, errorDescription, cause);
    }
}
