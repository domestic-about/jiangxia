// ============================================================================
// Domain i18n (en_US) · QC domain — CR-20260930-11 "QC documents" list section.
// Pairs with qc-list.zh_CN.ts (the two files must have the same key set). Top-level key: `list`.
// ============================================================================

export default {
  list: {
    title: 'QC documents',
    subtitle:
      'Organoid sample quality control records: one row per valid sample; the three status columns are its sample QC sheet, organoid QC sheet and organoid quality score sheet. Click "Open" to edit them.',
    filter: {
      keyword: 'Keyword',
      keywordPlaceholder: 'No. / source unit',
      sampleKind: 'Sample type',
      progress: 'Progress',
      receiveRange: 'Received on',
      receiveBegin: 'Start date',
      receiveEnd: 'End date'
    },
    kind: {
      tissue: 'Sample records',
      organoid: 'Organoid submissions'
    },
    progress: {
      none: 'Not started',
      doing: 'In progress',
      done: 'All completed'
    },
    status: {
      none: 'Not filled',
      draft: 'Draft',
      published: 'Completed'
    },
    col: {
      internalNo: 'Internal no.',
      sampleKind: 'Sample type',
      sourceUnit: 'Source unit',
      typeName: 'Tissue / organoid type',
      receiveDate: 'Received on',
      sampleQc: 'Sample QC',
      organoidQc: 'Organoid QC',
      score: 'Quality score',
      progress: 'Progress',
      lastUpdate: 'Last modified',
      action: 'Actions'
    },
    doneCount: '{n} / 3 completed',
    totalScore: '{n} pts',
    open: 'Open',
    search: 'Search',
    reset: 'Reset',
    refresh: 'Refresh',
    empty: 'No matching samples (only verified valid samples have QC documents)'
  }
};
