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
package nuxeo.quota.webui;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.nuxeo.common.utils.SizeUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.ecm.quota.size.QuotaSizeService;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.user.UserQuotaLimits;

/**
 * Shared utility methods for quota operations.
 *
 * @since 2025.1
 */
public final class QuotaUtils {

    /**
     * Framework property listing groups (comma-separated) whose members can manage quotas.
     * Defaults to {@value #ADMIN_GROUPS_DEFAULT}.
     */
    public static final String ADMIN_GROUPS_PROP = "nuxeo.quota.webui.admin.groups";

    public static final String ADMIN_GROUPS_DEFAULT = "administrators";

    /**
     * Framework property listing user IDs (comma-separated) who can manage quotas.
     * Defaults to empty.
     */
    public static final String ADMIN_USERS_PROP = "nuxeo.quota.webui.admin.users";

    public static final String ADMIN_USERS_DEFAULT = "";

    // Cached parsed values. Framework properties are loaded at startup and don't change
    // without a restart, so caching here is safe. The fields are volatile to make the
    // first publication visible to all threads.
    private static volatile Set<String> cachedAdminGroups;

    private static volatile Set<String> cachedAdminUsers;

    private static volatile Set<String> cachedExcludedPaths;

    private QuotaUtils() {
        // utility class
    }

    /* ==================== Admin / authorization ==================== */

    /**
     * Ensures the current user is allowed to manage quotas; throws {@link NuxeoException} with HTTP 403 otherwise.
     * <p>
     * A user is allowed if any of the following is true:
     * <ul>
     *   <li>the principal is a Nuxeo administrator ({@link NuxeoPrincipal#isAdministrator()})</li>
     *   <li>the principal's user ID is listed in {@value #ADMIN_USERS_PROP}</li>
     *   <li>the principal is a member of any group listed in {@value #ADMIN_GROUPS_PROP}</li>
     * </ul>
     *
     * @param session the core session to check
     * @throws NuxeoException if the current user is not allowed to manage quotas
     */
    public static void ensureAdmin(CoreSession session) {
        if (!isQuotaAdmin(session.getPrincipal())) {
            throw new NuxeoException("Quota administrator required", SC_FORBIDDEN);
        }
    }

    /**
     * Returns {@code true} if the given principal is allowed to manage quotas.
     *
     * @param principal the principal to check
     * @return {@code true} if the principal is a Nuxeo administrator, is listed in {@value #ADMIN_USERS_PROP},
     *         or belongs to a group listed in {@value #ADMIN_GROUPS_PROP}
     */
    public static boolean isQuotaAdmin(NuxeoPrincipal principal) {
        if (principal == null) {
            return false;
        }
        if (principal.isAdministrator()) {
            return true;
        }
        if (getAdminUsers().contains(principal.getName())) {
            return true;
        }
        var allowedGroups = getAdminGroups();
        if (allowedGroups.isEmpty()) {
            return false;
        }
        for (var group : principal.getAllGroups()) {
            if (allowedGroups.contains(group)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> getAdminGroups() {
        var groups = cachedAdminGroups;
        if (groups == null) {
            groups = parseCsv(Framework.getProperty(ADMIN_GROUPS_PROP, ADMIN_GROUPS_DEFAULT));
            cachedAdminGroups = groups;
        }
        return groups;
    }

    private static Set<String> getAdminUsers() {
        var users = cachedAdminUsers;
        if (users == null) {
            users = parseCsv(Framework.getProperty(ADMIN_USERS_PROP, ADMIN_USERS_DEFAULT));
            cachedAdminUsers = users;
        }
        return users;
    }

    /**
     * Invalidates the cached admin-groups and admin-users sets. Primarily for tests; the
     * production values come from framework properties and don't change at runtime.
     *
     * @since 2025.1
     */
    public static void invalidateAdminCache() {
        cachedAdminGroups = null;
        cachedAdminUsers = null;
    }

    /* ==================== Validation ==================== */

    /**
     * Throws {@link NuxeoException} with HTTP 400 if {@code value} is {@code null} or blank.
     *
     * @param value the value to check
     * @param fieldName the human-readable field name for the error message
     * @throws NuxeoException with HTTP 400 if {@code value} is blank
     */
    public static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new NuxeoException(fieldName + " is required", SC_BAD_REQUEST);
        }
    }

    /**
     * Parses a quota size string into bytes. Accepts {@code "-1"} as the "unlimited" sentinel.
     * Throws {@link NuxeoException} with HTTP 400 on invalid or negative input.
     *
     * @param val the size string (e.g. {@code "10 MB"}, {@code "-1"})
     * @return the parsed size in bytes, or {@code -1L} for unlimited
     * @throws NuxeoException with HTTP 400 if the value is invalid
     */
    public static long parseQuotaSize(String val) {
        if ("-1".equals(val)) {
            return -1L;
        }
        try {
            var parsed = SizeUtils.parseSizeInBytes(val);
            if (parsed < 0) {
                throw new NuxeoException("Invalid size: " + val, SC_BAD_REQUEST);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new NuxeoException("Invalid size: " + val, SC_BAD_REQUEST);
        }
    }

    /* ==================== JSON response helpers ==================== */

    /**
     * Builds the canonical JSON payload for the per-user quota endpoints
     * ({@code Quota.User.GetForCurrentUser}, {@code Quota.User.GetForUser}).
     *
     * @param userId the user ID
     * @param usedBytes the current used bytes
     * @param limits the resolved effective limits
     * @return a {@link JSONObject} ready to be serialized
     */
    public static JSONObject buildUserQuotaJson(String userId, long usedBytes, UserQuotaLimits limits) {
        var json = new JSONObject();
        json.put("userId", userId);
        json.put("usedBytes", usedBytes);
        json.put("maxTotalQuota", limits.maxTotalQuota());
        json.put("maxUploadSize", limits.maxUploadSize());
        json.put("source", limits.source());
        json.put("matchedGroup", limits.matchedGroup());
        return json;
    }

    /* ==================== Blob extraction ==================== */

    /**
     * Returns a new {@link BlobsExtractor} configured with the platform's excluded-paths list.
     * The excluded-paths list is cached after the first call (paths come from XML contributions
     * loaded at startup; restart is required to change them).
     *
     * @return a configured {@link BlobsExtractor}
     */
    public static BlobsExtractor newBlobsExtractor() {
        var extractor = new BlobsExtractor();
        extractor.setExtractorProperties(null, getExcludedPaths(), true);
        return extractor;
    }

    private static Set<String> getExcludedPaths() {
        var paths = cachedExcludedPaths;
        if (paths == null) {
            var sizeService = Framework.getService(QuotaSizeService.class);
            paths = new HashSet<>(sizeService.getExcludedPathList());
            cachedExcludedPaths = paths;
        }
        return paths;
    }

    /**
     * Invalidates the cached excluded-paths set. Primarily for tests.
     *
     * @since 2025.1
     */
    public static void invalidateExcludedPathsCache() {
        cachedExcludedPaths = null;
    }

    /* ==================== Internal ==================== */

    private static Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toCollection(HashSet::new));
    }
}
