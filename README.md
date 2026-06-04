# nuxeo-quota-webui



## Features

This plugin brings Web UI to [nuxeo-quota](https://doc.nuxeo.com/nxdoc/nuxeo-quota/). The nuxeo-quota plugin UI is deprecated (depending on JSF). This plugin takes what was done in JSF (via SEAM beans) and converts them to WebUI + java operations.

Basically:

* Move the deprecated JSF code found at `countQuotaStatsActions.java` (nuxeo-jsf-ui-lts/code/nuxeo-quota-web/src/main/java/org/nuxeo/ecm/quota/countQuotaStatsActions.java) to some operations (to get/set quota on User Workspaces, etc.)
* Add the different polymer elements required by the UI.

**Parity with the legacy JSF plugin:**
* "Quotas / Statistics" Admin Center page (pie-chart stats, User Workspace quota activation, initial computation)
* "Quota / Statistics" tab on containers (`Domain`, `Workspace`, configurable)

**New features (not available in the legacy JSF `nuxeo-quota` plugin):**
* Per-user **total quota** (sum of bytes owned via `dc:creator`)
* Per-user **per-blob upload size cap**
* (Configurable with XML contribution, and can be modified by an administrator in the UI)

## Usage

#### "Quota / Statistics" Admin panel

* Display pie-chart stats
* Activate User Workspaces quota, setting the max size for every User Workspace.
  * The max possible value is by default 999 GB
* Run counters for the first time, on existing (and current) repository.

The work is done asynchronously, since it can take time depending on the number of users and the volume of content in each User Workspace.

![Admin Center/Quota](readme-resources/quota-admin.jpg)

#### New Tab at Container Level


By default, the tab is displayed for `Domain` and `Workspace`. This can be tuned by setting the `nuxeo.quota.containersfilter` configuration parameter in `nuxeo.conf`. It is a comma-separated list, which is directly used in a `nuxeo-filter` element in WebUI. For example:

```
nuxeo.quota.containersfilter=Domain,Workspace,MyCustomContainer
```

When available for document, it displays "Quota / Statistics" tab with:

* A pie-chart of usage
* If user is Administrator or has the `Everything` permission (like in the deprecated version), then the quota can be set or unset.


![Admin Center/Quota](readme-resources/quota-document.jpg)

#### Per-user Upload Size and Per-user Quota

> [!NOTE]
> These per-user features did **not** exist in the legacy JSF `nuxeo-quota` plugin. They are added by this plugin and live alongside the original container-level quota.

Two enforcement mechanisms:

- **Per-blob upload size cap** — each individual file (blob) is checked against the user's effective `maxUploadSize`.
- **Per-user total quota** — the sum of all blob bytes owned by a user (keyed by `dc:creator`) is checked against the user's effective `maxTotalQuota`.

**Owner attribution gotcha.** Bytes are always counted against `dc:creator` of the document — never the editor. Because Nuxeo resets `dc:creator` on copy, copying a document counts those bytes against the user who performed the copy, not the original creator.

##### XML defaults

Configured via the `userQuotas` extension point on `nuxeo.quota.webui.user.UserQuotaService`:

```xml
<userQuota group="*" maxUploadSize="-1" maxTotalQuota="-1"/>
<userQuota group="members" maxUploadSize="50 MB" maxTotalQuota="2 GB"/>
```

##### Resolution order

For each limit key (`maxUploadSize`, `maxTotalQuota`):

1. User override (if any)
2. Group overrides for the user's groups — **max-wins**
3. XML group defaults for the user's groups — **max-wins**
4. XML `*` default
5. Unlimited

Administrators bypass enforcement. `-1` means **unlimited** (explicitly different from "no override / fall through").

##### Admin Center — "User Quotas" card

The "Quotas / Statistics" admin page has a new **User Quotas** card where administrators can:
* View the resolved XML defaults
* Add / edit / remove **group overrides**
* Add / edit / remove **user overrides**

Changes take effect immediately (no restart, no redeploy).

![User Quotas admin card](readme-resources/user-quotas-admin-card.jpg)

##### End-user "My quota usage" in the user menu

A new entry is contributed to the user menu (`USER_MENU_ITEMS` slot) showing the current user their own used bytes, effective limit, and percentage. Users without a configured limit see an "unlimited" message.

![User Quota in user menu](readme-resources/user-quotas-user-menu.jpg)

##### Initial computation

For an existing repository, launch a one-time computation from the Admin Center → **Compute Initial Statistics** → **Per-user quota**. It runs asynchronously as a `Work` (`UserQuotaInitialComputationWork`) and populates the per-user counters.

![Initial computation](readme-resources/user-quotas-initial-compute.jpg)

##### Storage

Counters and overrides are persisted in `KeyValueService` named stores:

| Store | Purpose |
|---|---|
| `quota-user-counters` | Per-user current byte total |
| `quota-group-overrides` | Per-group live overrides |
| `quota-user-overrides` | Per-user live overrides |

In production these are automatically backed by the configured `default` key/value store (MongoDB or SQL) and are therefore **cluster-shared** with no extra configuration.

##### Automation operations (`Quota.User.*`)

You should not need to use these operations, they are listed here FYI.

| Operation ID | Purpose |
|---|---|
| `Quota.User.GetConfiguration` | Read XML defaults + all group/user overrides (admin) |
| `Quota.User.GetForCurrentUser` | Resolved limits + current usage for the caller |
| `Quota.User.GetForUser` | Resolved limits + usage for any user (admin) |
| `Quota.User.SetGroupOverride` | Create/update a group-level override (admin) |
| `Quota.User.ClearGroupOverride` | Remove a group-level override (admin) |
| `Quota.User.SetUserOverride` | Create/update a user-level override (admin) |
| `Quota.User.ClearUserOverride` | Remove a user-level override (admin) |
| `Quota.User.RecomputeForUser` | Recompute the counter for one user (admin) |

## Known Issue(s)

#### Labels Display Translation Keys
Sometime, the localization file is not deployed. It is located at `nuxeo-quota-webui/nuxeo-quota-webui-ui/src/main/resources/web/nuxeo.war/ui/i18n`.

If you don't see the labels ("action.activate.quota" instead of "Activate" for example), then the work around is the following:

1. Copy the values found in the messages.json file.
  * Do not copy the beginning/ending `{` and `}`
2. Typically, paste the values in your Studio project > Modeler > UI > Translations > messages.json.
  * ⚠️ Make sure you paste with no JSON syntax error (don't forget the `,` after the last line after pasting, for example)
3. Deploy your Studio project


#### Error Handling
When a user uploads content that makes the container reach its quota — or that exceeds the per-user `maxTotalQuota` / `maxUploadSize` — the error displayed in the UI is not explicit. The creation dialog just displays that "an error occurred" (the error is explicit in `server.log`). We are missing some error bubbling and handling to make it clear to the end user that they reached a quota.


## Possible `TODO`

Converting the deprecated JSF actions to Automation operations called by the UI is a valid pattern that works well.

At platform level, a [Pull request](https://doc.nuxeo.com/nxdoc/contributing-to-nuxeo/) contributing Nuxeo source code could be made, adding all this to the `nuxeo-quota` core code that is available in the platform (listeners that check the quotas, activation/de-activation of quotas, etc.). So, things that could be done:

* Move the java code of this plugin (operations, etc.) to the nuxeo-quota module
* WebUI: Bubble the error when the quota is reached
* Tune the UI global look & feel, make it look, maybe, a bit better.


## Deploy / Build and Deploy

### Build and Deploy Locally

```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-quota-webui
cd nuxeo-quota-webui
mvn clean install
```

To skip unit testing, add `-DskipTests`.

The Marketplace package is generated at:
```
nuxeo-quota-webui-package/target/nuxeo-quota-webui-package-{VERSION}}-*.zip
```

Install it via `nuxeoctl`:
```bash
nuxeoctl mp-install nuxeo-quota-webui-package-{VERSION}.zip
```

### Deploy from Nuxeo Marketplace

This plugin is available as a package on the [Nuxeo Marketplace](https://connect.nuxeo.com/nuxeo/site/marketplace/package/nuxeo-quota-webui), you can just:

```bash
nuxeoctl mp-install nuxeo-quota-webui
```


## Support

**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into the platform, not maintained here.

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

## About Nuxeo

Nuxeo Platform is an open source highly scalable, cloud-native, enterprise content management product with rich multimedia support, written in Java. Data can be stored in both SQL & NoSQL databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

More information is available at [Hyland/Nuxeo](https://www.hyland.com/en/solutions/products/nuxeo-platform).
