package org.opendatamesh.platform.pp.blueprint.client.notification;

import java.util.List;

public interface NotificationClient {
    void assertConnection();
    void subscribeToEvents(List<String> eventTypes);
    void notifyEvent(Object event);
    void processingSuccess(Long notificationId);
    void processingFailure(Long notificationId);
}