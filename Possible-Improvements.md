# Possible Improvements

Tracked ideas not yet implemented. Each one stands on its own — pick and choose.

## UI

### Filter form on the "heaviest" listings
The deprecated JSF UI exposed a filter form on top of the `quota_heaviest_children`
and `quota_heaviest_containers` listings (`dc:title LIKE`, `dss:totalSize BETWEEN
min/max`). Not ported. Could be added via a `<nuxeo-search-form>`-style header
above the `<nuxeo-results>`. Two predicates would need to be added to the page
providers' XML contributions, mirroring the JSF `<predicate>` definitions in:
`nuxeo-jsf-ui-lts/code/nuxeo-quota-web/src/main/resources/OSGI-INF/quota-contentviews-contrib.xml`.

### Bubble up the quota-exceeded error in upload dialogs
When a user uploads content that pushes the container over its quota, the creation
dialog shows a generic "an error occurred" message. `server.log` has the real
detail. Web UI should surface the actual error message to the end user. (Already
noted in `README.md`.)

### Column selection on the "heaviest" listings
`<nuxeo-data-table settings-enabled="true">` is already set, which exposes a
column-visibility menu in the table header. Verify it works as expected in
practice; if not, look at the JSF `edit_columns` mode for parity.

### Translated size labels in the pie chart
The pie-chart legend currently uses hardcoded English (`Live Documents`, `Trash`,
`Archived Versions`) and hardcoded `B/KB/MB/GB` units — see
`nuxeo-quota-stats.html` `_SIZE_LABELS`. Per user decision (Option B), units stay
hardcoded too. If multi-language support is needed later, switch to passing
`language` to `Quotas.GetStatistics` and reading `obj.label` split on `:`.

### Loading indicator on the "heaviest" listings
The page-provider query may take a moment on large repositories. No spinner is
currently shown — the table just renders empty until results arrive. Add a
`<paper-spinner>` or rely on `<nuxeo-results>`'s built-in loading state if it
exposes one.

### True lazy-load on the "heaviest" listings
Both UIs render the heaviest list inside a `<section>` of an `<iron-pages>`,
which keeps the section in the DOM even when the sibling "stats" tab is the
active one. The page provider therefore fires its query on attach, before the
user ever clicks the "heaviest" tab (single `LIMIT 40` NXQL, cheap but not
free). Cheapest fix: wrap each `<section>` body in a `<template is="dom-if"
if="[[_isHeaviestTab(selectedTab)]]">` so the inner `<nuxeo-quota-heaviest-list>`
only stamps when its tab is selected. (Historical note: the admin center
previously used `<nuxeo-card collapsible>` for this listing. Lazy-loading via
`dom-if` gated on `opened` didn't work because `<nuxeo-card>` does not declare
`notify: true` on its `opened` property — see
`nuxeo-elements/ui/widgets/nuxeo-card.js:180-184` — so two-way binding
silently degraded to one-way down, the `dom-if` never restamped, and the card
appeared to do nothing on click. The sub-tab refactor removed this constraint.)

## Backend / Operations

### Move the plugin into platform `nuxeo-quota`
Already noted in `README.md`. Contributing the operations + Web UI elements +
page providers upstream would replace the deprecated JSF UI directly.

### Configurable `dss:totalSize` minimum threshold for the listings
The two page providers in `page-providers-contrib.xml` hardcode
`AND dss:totalSize >= 1024` to mirror the JSF behavior (hide near-empty
containers). Could be parameterised via a `nuxeo.conf` property
(e.g. `nuxeo.quota.heaviest.minBytes=1024`) and read into the page provider XML
via property substitution.

### Scope override for `quota_heaviest_containers`
Today the admin-center embed always passes `["/"]` (whole repository). Could
expose a path input in the admin UI to scope the listing to a sub-tree.

### Smoke test for the new page providers
No Java test currently asserts that `quota_heaviest_children` /
`quota_heaviest_containers` resolve and return results. A small JUnit test
deploying both bundles, creating Workspaces with files, running
`Quota.LaunchInitialComputation` then resolving the provider via
`PageProviderService.getPageProvider(...)` would catch NXQL typos at build time.
