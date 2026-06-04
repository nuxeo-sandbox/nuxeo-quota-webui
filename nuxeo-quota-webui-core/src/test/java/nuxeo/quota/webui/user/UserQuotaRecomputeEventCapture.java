/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package nuxeo.quota.webui.user;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;

/**
 * Test-only synchronous listener that captures {@code userQuotaRecomputeDone} event properties
 * for assertion in unit tests.
 */
public class UserQuotaRecomputeEventCapture implements EventListener {

    public static final ConcurrentLinkedQueue<Map<String, Object>> CAPTURED = new ConcurrentLinkedQueue<>();

    public static void reset() {
        CAPTURED.clear();
    }

    @Override
    public void handleEvent(Event event) {
        if (UserQuotaService.EVENT_USER_QUOTA_RECOMPUTE_DONE.equals(event.getName())) {
            CAPTURED.add(new java.util.HashMap<>(event.getContext().getProperties()));
        }
    }
}
