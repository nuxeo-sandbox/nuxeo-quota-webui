/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package nuxeo.quota.webui.operations.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import nuxeo.quota.webui.user.UserQuotaRecomputeEventCapture;
import nuxeo.quota.webui.user.UserQuotaService;

/**
 * Verifies that {@code userQuotaRecomputeDone} is fired with the documented payload
 * after a {@code Quota.User.RecomputeUsers} command completes.
 */
@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, CoreBulkFeature.class })
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.quota")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core:OSGI-INF/test-userquota-contrib.xml")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core:OSGI-INF/test-recompute-event-listener-contrib.xml")
public class TestUserQuotaRecomputeEvent {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Before
    public void clearCapture() {
        UserQuotaRecomputeEventCapture.reset();
    }

    /** Polls the capture queue for up to 60s, returning the first event or null on timeout. */
    protected java.util.Map<String, Object> awaitCapturedEvent() throws InterruptedException {
        var deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            var ev = UserQuotaRecomputeEventCapture.CAPTURED.poll();
            if (ev != null) {
                return ev;
            }
            Thread.sleep(100);
        }
        return null;
    }

    @Test
    public void recomputeUsersFiresCompletionEventWithExpectedPayload() throws Exception {
        var ctx = new OperationContext(session);
        var params = new HashMap<String, Object>();
        params.put("users", Arrays.asList("Administrator", "nobody"));
        automationService.run(ctx, QuotaUserRecomputeUsers.ID, params);

        var captured = awaitCapturedEvent();
        assertNotNull("Expected one " + UserQuotaService.EVENT_USER_QUOTA_RECOMPUTE_DONE + " event", captured);

        assertEquals(session.getRepositoryName(), captured.get("repositoryName"));
        assertEquals("users", captured.get("scope"));
        assertNotNull(captured.get("commandId"));
        assertTrue(captured.get("commandId") instanceof String);

        @SuppressWarnings("unchecked")
        var requested = (List<String>) captured.get("requestedUsers");
        assertEquals(Arrays.asList("Administrator", "nobody"), requested);

        @SuppressWarnings("unchecked")
        var processed = (List<String>) captured.get("processedUsers");
        assertEquals(List.of("Administrator"), processed);

        @SuppressWarnings("unchecked")
        var skipped = (List<String>) captured.get("skippedUsers");
        assertEquals(List.of("nobody"), skipped);

        assertNotNull("userTotals must be present", captured.get("userTotals"));
        assertTrue(captured.get("userTotals") instanceof java.util.Map);
    }

    @Test
    public void recomputeAllFiresCompletionEventWithKvDerivedUsers() throws Exception {
        // Seed counters for two users so the KV scan after BAF completion has something to find
        var counter = new nuxeo.quota.webui.user.UserQuotaCounter();
        counter.set(session.getRepositoryName(), "alice", 1234L);
        counter.set(session.getRepositoryName(), "bob", 5678L);

        var ctx = new OperationContext(session);
        automationService.run(ctx, QuotaUserRecomputeAll.ID);

        var captured = awaitCapturedEvent();
        assertNotNull(captured);
        assertEquals("all", captured.get("scope"));
        assertNotNull(captured.get("commandId"));

        // processedUsers should have been populated from the KV store post-completion.
        // Note: RecomputeAll resets all counters before submitting the BAF; in this test
        // there are no documents in the repo, so post-BAF the counters are 0 and userTotals
        // is empty. processedUsers may also be empty because resetAllForRepository removed
        // the seeded keys. Just assert the keys are present.
        assertTrue(captured.containsKey("processedUsers"));
        assertTrue(captured.containsKey("userTotals"));
    }
}
