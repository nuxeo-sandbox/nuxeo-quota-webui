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

/**
 * Effective per-user quota limits after resolution.
 *
 * @since 2025.1
 */
public record UserQuotaLimits(long maxUploadSize, long maxTotalQuota, String source, String matchedGroup) {

    public static final String SOURCE_ADMIN_BYPASS = "admin-bypass";
    public static final String SOURCE_USER_OVERRIDE = "user-override";
    public static final String SOURCE_GROUP_OVERRIDE = "group-override";
    public static final String SOURCE_GROUP_DEFAULT = "group-default";
    public static final String SOURCE_WILDCARD_DEFAULT = "wildcard-default";
    public static final String SOURCE_UNLIMITED = "unlimited";

    public boolean isAdminBypass() {
        return SOURCE_ADMIN_BYPASS.equals(source);
    }
}
