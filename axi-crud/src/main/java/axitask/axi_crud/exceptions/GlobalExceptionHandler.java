package axitask.axi_crud.exceptions;

import axitask.axi_crud.DTO.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@RestController
public class GlobalExceptionHandler {

    @ExceptionHandler(LogicException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(LogicException ex,
                                                                 WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getErrorCode(),
                ex.getErrorDescription()
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorResponse> handleJsonProcessingException(JsonProcessingException ex,
                                                                       WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getErrorCode(),
                ex.getErrorDescription()
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(errorResponse, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex,
                                                               WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                "INTERNAL_ERROR",
                "Internal server error occurred"
        );
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
