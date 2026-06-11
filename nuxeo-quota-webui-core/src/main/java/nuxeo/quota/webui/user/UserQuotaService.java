/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package nuxeo.quota.webui.user;

import java.util.List;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;

/**
 * Service for per-user quota limit resolution.
 *
 * @since 2025.1
 */
public interface UserQuotaService {

    /** Framework property to globally disable the per-user quota listener. */
    String PROPERTY_DISABLED = "nuxeo.quota.user.disabled";

    /** Event fired when a user quota recompute completes. */
    String EVENT_USER_QUOTA_RECOMPUTE_DONE = "userQuotaRecomputeDone";

    /** Effective limits for a principal, after applying overrides. */
    UserQuotaLimits getEffectiveLimits(NuxeoPrincipal p, String repositoryName);

    /** XML defaults only (no overrides), for the admin UI. */
    List<UserQuotaDescriptor> getXmlDefaults();

    /** Convenience for ops layer; same as getEffectiveLimits but accepts a userId. */
    UserQuotaLimits getEffectiveLimitsForUser(String userId, String repositoryName);

    /** @since 2025.1 */
    boolean isEnabled();

    /** @since 2025.1 */
    void setEnabled(boolean enabled);

    /** @since 2025.1 */
    void invalidateCache();

    /** @since 2025.1 */
    void invalidateCacheForUser(String userId, String repositoryName);

    /**
     * Returns the shared {@link UserQuotaOverrideStore} instance used by the service.
     * <p>
     * The store is stateless (it only holds references to the underlying KV stores) but
     * exposing it through the service avoids allocating a fresh wrapper in every operation.
     *
     * @since 2025.1
     */
    UserQuotaOverrideStore getOverrideStore();
}
