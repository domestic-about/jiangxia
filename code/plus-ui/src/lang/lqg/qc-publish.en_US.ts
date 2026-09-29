// ============================================================================
// Domain i18n (en_US) · QC domain — DOC-PUBLISH-001 (preview pane + publish/withdraw)
// Mirrors src/lang/lqg/qc-publish.zh_CN.ts; keep both key sets identical.
// ============================================================================

export default {
  preview: {
    audienceInternal: 'Internal',
    audienceExternal: 'External',
    internalHint: 'Internal version keeps the internal no.',
    externalHint: 'Internal no. is blank in the external version',
    refresh: 'Refresh',
    rendering: 'Generating page images…',
    failedTitle: 'This document failed to render',
    noReason: '(the backend gave no reason)',
    regenerate: 'Regenerate',
    notGenerated: 'No page images generated for this document yet',
    preview: 'Preview',
    pageNo: 'Page {n}',
    downloadTitle: 'Download (same artifact as the preview)',
    downloadWord: 'Download Word',
    downloadPdf: 'Download PDF',
    downloadMergedWord: 'Download merged Word',
    downloadMergedPdf: 'Download merged PDF',
    mergedNotForScore: 'The score sheet has no standalone merged file: merged = the completed sheets in a fixed order',
    mergedUnavailable: 'Merged file is not available yet: ',
    needsResync: 'This document is still a draft and the submitter cannot see it. Click Complete & sync to publish it.',

    publishDone: 'Synced to the submitter',
    publishFailed: 'Complete & sync failed: ',
    unpublishDone: 'Withdrawn; the submitter can no longer see it',
    unpublishFailed: 'Withdraw failed: ',
    unpublish: 'Withdraw',
    saveFirst: 'Save the current tab as a draft before completing & syncing',
    publishConfirm: 'Complete & sync this document? The submitter will be able to see it afterwards.',
    unpublishConfirm: 'Withdraw this document? The submitter stops seeing it immediately.',
    syncedFooter: 'Synced to the submitter · re-sync after editing'
  }
};
