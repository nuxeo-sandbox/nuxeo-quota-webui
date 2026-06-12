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
When opening the collapsible card, the page-provider query may take a moment on
large repositories. No spinner is currently shown — the table just renders empty
until results arrive. Add a `<paper-spinner>` or rely on `<nuxeo-results>`'s
built-in loading state if it exposes one.

### True lazy-load on the "heaviest" listings
Currently the page provider fires its query as soon as the page renders, even
when the card is collapsed (single `LIMIT 40` NXQL, cheap but not free). Reason:
`<nuxeo-card>` does not declare `notify: true` on its `opened` property
(`nuxeo-elements/ui/widgets/nuxeo-card.js:180-184`), so we can't gate a
`<template is="dom-if">` on the card's state via two-way binding without losing
the toggle (the parent flag never receives the change, the `dom-if` never
restamps, and the card appears to do nothing on click). Two fixes possible:
either patch `<nuxeo-card>` upstream to add `notify: true` on `opened` (Web UI
PR), or wrap the listing in our own collapse element with a custom header.

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
