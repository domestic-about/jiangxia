// ============================================================================
// 域内 i18n（en_US）· EMBED 域 —— EMBED-WEB-001（工作台石蜡包埋页）
//
// 键集合必须与 zh_CN 完全一致（SYS-WEB-001 的约定）。
// ============================================================================

export default {
  title: 'Paraffin embedding records',
  subtitle: 'Internal and external submissions in one table: pending external ones are pinned on top with a pale row; export matches the client template column by column.',
  search: 'Search',
  reset: 'Reset',
  loading: 'Loading…',
  empty: 'No paraffin embedding record matches the filters',

  filter: {
    paraffinBlockNo: 'Block no.',
    paraffinBlockNoPlaceholder: 'Block no. (fuzzy)',
    internalNo: 'Sample no.',
    internalNoPlaceholder: 'Internal no. (exact)',
    stain: 'Stain',
    sectionTimeRange: 'Section time',
    sectionTimeBegin: 'Section from',
    sectionTimeEnd: 'Section to',
    verifyStatus: 'Verify status',
    submitSource: 'Source',
    submitSourceAll: 'All',
    sampleFilter: 'Only embedding records of sample {id}'
  },

  toolbar: {
    add: 'New embedding record',
    refresh: 'Refresh',
    export: 'Export paraffin embedding records',
    exporting: 'Exporting…',
    exportDone: 'Export finished',
    exportEmpty: 'No record to export under the current filters'
  },

  col: {
    submitSource: 'Source',
    verifyStatus: 'Verify status',
    paraffinBlockNo: 'Block no.',
    internalNo: 'Sample no.',
    sampleType: 'Sample type',
    organoidSourceType: 'Organoid source type',
    tissueReceiveTime: 'Tissue receive time',
    tissueProcessTime: 'Tissue process time',
    agaroseEmbedTime: 'Agarose embedding time',
    embedBy: 'Embedded by',
    dehydrateTime: 'Dehydration time',
    agaroseSendTime: 'Agarose submission time',
    paraffinEmbedTime: 'Paraffin embedding time',
    sectionTime: 'Section time',
    stain: 'Stain',
    markerExpression: 'mark expression',
    operatorName: 'Operator',
    remark: 'Remark',
    updateTime: 'Last modified',
    action: 'Action'
  },

  badge: {
    pendingBlockNo: 'Pending'
  },

  rowAction: {
    verify: 'Verify',
    edit: 'Edit',
    delete: 'Delete',
    deleteConfirm: 'Delete the embedding record of block "{no}"?',
    deleted: 'Deleted',
    readonlyTip: 'Pending / invalid submissions can only be handled by verification',
    sectioned: 'Sectioned',
    notSectioned: 'Not sectioned'
  },

  cell: {
    stainNone: 'No stain',
    stainOther: 'Other',
    stainOtherFull: 'Other ({name})',
    noStain: '—',
    markerColon: ': ',
    markerSeparator: '; '
  },

  drawer: {
    addTitle: 'New embedding record',
    editTitle: 'Edit embedding record',
    verifyTitle: 'Verify embedding submission',
    sectionEmbed: 'Embedding',
    sectionProcess: 'Process times',
    sectionStain: 'Stain and markers',
    sectionOther: 'Operator and remark',
    lastModified: 'Last modified: {name} · {time}',
    lastModifiedNever: 'Last modified: never',
    save: 'Save',
    cancel: 'Cancel',
    saved: 'Saved',
    loadFailed: 'Load failed, please retry',
    required: 'Required',

    sample: 'Sample',
    samplePlaceholder: 'Search by internal no. (verified samples only)',
    sampleSearching: 'Searching…',
    sampleNoResult: 'No verified sample matches',
    sampleRequired: 'Please pick the linked sample',
    sampleLocked: 'The linked sample is fixed',
    sampleInternalNo: 'Internal no.',
    sampleSubmitNo: 'Submission no.',
    sampleTissueReceive: 'Receive date',

    paraffinBlockNo: 'Block no.',
    paraffinBlockNoPlaceholder: 'e.g. T-E01-1 (globally unique)',
    paraffinBlockNoRequired: 'Block no. is required',
    sampleType: 'Sample type',
    organoidSourceType: 'Organoid source type',
    tissueReceiveTime: 'Tissue receive time',
    tissueProcessTime: 'Tissue process time',
    agaroseEmbedTime: 'Agarose embedding time',
    embedBy: 'Embedded by',
    dehydrateTime: 'Dehydration time',
    agaroseSendTime: 'Agarose submission time',
    paraffinEmbedTime: 'Paraffin embedding time',
    sectionTime: 'Section time',
    stain: 'Stain',
    stainOther: 'Stain name',
    stainOtherPlaceholder: 'e.g. Masson',
    stainNoneExclusive: '"No stain" is exclusive with other stains',
    stainOtherRequired: '"Other" requires a concrete stain name',
    marker: 'Markers',
    markerName: 'Name',
    markerNamePlaceholder: 'e.g. Ki67',
    markerExpression: 'Expression',
    markerAdd: 'Add marker',
    markerRemove: 'Remove',
    operatorName: 'Operator',
    remark: 'Remark',

    verifyStatus: 'Verify status',
    submitSource: 'Source',
    invalidReason: 'Invalid reason',
    invalidReasonPlaceholder: 'The external user sees this sentence',
    invalidReasonRequired: 'Please write the invalid reason',
    verifyHint: 'Approving requires a block no. (globally unique); afterwards process times and stains can be filled as usual. Rejecting requires a reason.',
    saveValid: 'Approve and save',
    saveInvalid: 'Reject',
    verifiedValid: 'Approved',
    verifiedInvalid: 'Rejected',
    invalidReasonTitle: 'Reason for rejection',
    sampleNotVerified: 'The linked sample is not verified valid yet (current: {status}), cannot approve',
    sampleVerifiedTip: 'The linked sample is verified valid, approval is allowed',

    stainHint: 'Multiple choice; "No stain" is exclusive with the rest; "Other" requires a concrete name.',
    markerHint: 'One marker per row; a row without a name writes the expression only (same in the export).',

    untitledMarker: '(unnamed)'
  },

  msg: {
    exportNotYet: 'Export is not wired yet',
    loadUnitsFailed: 'Failed to load units / groups',
    /** Fallback when the backend msg is unavailable (issue #145) */
    saveFailed: 'Save failed, please retry',
    verifyFailed: 'Verification failed, please retry'
  }
};
