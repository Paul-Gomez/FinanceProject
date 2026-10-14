package com.fincore.notifications.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;

import java.util.UUID;

public class NotificationNotFoundException extends BusinessException {

    public NotificationNotFoundException(UUID notificationId) {
        super(ErrorType.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "No existe ninguna notificación con id " + notificationId);
    }
}
