package com.fincore.imports.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

/** El fichero entero no se puede procesar (vacío, sin cabeceras reconocibles, demasiado grande...). */
public class InvalidImportFileException extends BusinessException {

    public InvalidImportFileException(String reason) {
        super(ErrorType.UNPROCESSABLE, "INVALID_IMPORT_FILE", "El fichero no se puede importar: " + reason);
    }
}
