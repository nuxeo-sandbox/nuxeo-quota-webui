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

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * Descriptor for per-user quota XML contributions.
 * <p>
 * {@code group} may be a specific group name or {@code "*"} to match all users not matched by more specific groups.
 * {@code "-1"} means unlimited.
 *
 * @since 2025.1
 */
@XObject("userQuota")
public class UserQuotaDescriptor {

    @XNode("@group")
    protected String group;

    @XNode("@maxUploadSize")
    protected String maxUploadSize;

    @XNode("@maxTotalQuota")
    protected String maxTotalQuota;

    public UserQuotaDescriptor() {
    }

    /** Copy constructor, used by DescriptorRegistry merge. */
    public UserQuotaDescriptor(UserQuotaDescriptor other) {
        this.group = other.group;
        this.maxUploadSize = other.maxUploadSize;
        this.maxTotalQuota = other.maxTotalQuota;
    }

    public String getGroup() {
        return group;
    }

    public String getMaxUploadSize() {
        return maxUploadSize;
    }

    public String getMaxTotalQuota() {
        return maxTotalQuota;
    }

    public UserQuotaDescriptor merge(UserQuotaDescriptor other) {
        UserQuotaDescriptor merged = new UserQuotaDescriptor(this);
        if (other.maxUploadSize != null) {
            merged.maxUploadSize = other.maxUploadSize;
        }
        if (other.maxTotalQuota != null) {
            merged.maxTotalQuota = other.maxTotalQuota;
        }
        return merged;
    }
}
