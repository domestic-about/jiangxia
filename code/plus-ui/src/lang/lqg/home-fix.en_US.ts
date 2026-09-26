// ============================================================================
// Domain i18n (en_US) · SYS home — independent-acceptance fixes (2026-09-23, F2: render issues list)
// Mirrors src/lang/lqg/home-fix.zh_CN.ts; keep both key sets identical.
// ============================================================================

export default {
  issues: {
    cardTitle: 'Render failures / missing images',
    cardHint: 'QC documents that failed to render or miss images; open the list to regenerate',
    cardZero: 'No failed renders or missing images',
    cardGo: 'Open list →',
    title: 'Render failures and missing images',
    subtitle: 'One row per document version; fix the images or content, then Regenerate — fixed rows disappear',
    empty: 'Nothing failed or missing right now',
    loadFailed: 'Could not load the list: {msg}',
    colSample: 'Sample',
    colDoc: 'Document',
    colAudience: 'Version',
    colIssue: 'Issue',
    colTime: 'Time',
    colAction: 'Actions',
    failed: 'Render failed',
    missing: 'Internal version misses {n} image(s)',
    internal: 'Internal',
    external: 'External',
    openQc: 'Open QC page',
    regenerate: 'Regenerate',
    regenerated: 'Regenerated; list refreshed',
    stillFailed: 'Still failing after regenerating: {msg}',
    stillMissing: 'Still missing {n} image(s) after regenerating',
    docSampleQc: 'Sample QC sheet',
    docOrganoidQc: 'Organoid QC sheet',
    docScore: 'Organoid quality score sheet',
    docMerged: 'Merged'
  }
};
