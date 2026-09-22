// ============================================================================
// 域内 i18n（en_US）· CRYO 域 —— CRYO-WEB-001（工作台冻存管理页）
//
// 键集合必须与 zh_CN 完全一致（SYS-WEB-001 的约定）。
// ============================================================================

export default {
  title: '-80 cryo management',
  subtitle: 'Overdue batches are pinned on top with a pale red row; withdraw / replenish / stock-take and editing or deleting a record are workbench-only, and the export matches the client template column by column.',
  search: 'Search',
  reset: 'Reset',
  loading: 'Loading…',
  empty: 'No cryo batch matches the filters',

  tab: {
    all: 'All',
    overdue: '-80 overdue',
    ln2: 'Liquid nitrogen'
  },

  filter: {
    internalNo: 'Sample no.',
    internalNoPlaceholder: 'Internal no. (exact)',
    cryoName: 'Cryo sample',
    cryoNamePlaceholder: 'Cryo sample name (fuzzy)',
    location: 'Location',
    locationAll: 'All',
    locationMinus80: '-80 freezer',
    locationLn2: 'Liquid nitrogen',
    overdueOnly: 'Overdue only',
    freezeTimeRange: 'Freeze time',
    freezeTimeBegin: 'Freeze from',
    freezeTimeEnd: 'Freeze to',
    sampleFilter: 'Only cryo batches of sample {id}'
  },

  toolbar: {
    add: 'New cryo batch',
    refresh: 'Refresh',
    export: 'Export -80 cryo',
    exporting: 'Exporting…',
    exportDone: 'Export finished',
    exportEmpty: 'No batch to export with the current filters'
  },

  col: {
    freezeTime: 'Freeze time',
    cryoName: 'Cryo sample',
    initQty: 'Frozen qty (tubes)',
    density: 'Density',
    inMinus80: 'Stored in -80 ultra-low freezer',
    frozenBy: 'Frozen by',
    toLn2Time: 'Moved from -80 to liquid nitrogen at',
    ln2Location: 'LN2 location',
    remark: 'Remark',
    internalNo: 'Sample no.',
    passage: 'Passage',
    remainingQty: 'Remaining (tubes)',
    location: 'Location',
    overdue: 'Overdue',
    updateTime: 'Last modified',
    action: 'Action'
  },

  badge: {
    overdue: '{days} days overdue',
    overdueToday: 'Due today',
    emptyQty: 'Used up'
  },

  flag: {
    yes: 'Yes',
    no: 'No'
  },

  location: {
    minus80: '-80 freezer',
    ln2: 'Liquid nitrogen'
  },

  flowType: {
    take: 'Withdraw',
    add: 'Replenish',
    adjust: 'Stock-take',
    unknown: '—'
  },

  rowAction: {
    take: 'Withdraw',
    add: 'Replenish',
    adjust: 'Stock-take',
    toLn2: 'To LN2',
    flow: 'Records',
    edit: 'Edit',
    delete: 'Delete',
    deleteConfirm: 'Delete cryo batch "{name}"? A batch with records that are not deleted cannot be removed.',
    deleted: 'Deleted',
    notYet: 'Not implemented yet'
  },

  drawer: {
    addTitle: 'New cryo batch',
    editTitle: 'Edit cryo batch',
    sectionBatch: 'Batch',
    sectionStore: 'Storage',
    lastModified: 'Last modified: {name} · {time}',
    lastModifiedNever: 'Last modified: never',
    save: 'Save',
    cancel: 'Cancel',
    saved: 'Saved',
    loadFailed: 'Failed to load, please retry',

    sample: 'Sample',
    samplePlaceholder: 'Search by internal no. (only verified-valid samples)',
    sampleRequired: 'Please pick the sample',
    sampleLocked: 'The sample is fixed and cannot be changed',

    cryoName: 'Cryo sample',
    cryoNamePlaceholder: 'Free text, e.g. T-hli01-GZ-N-P2-EM2-2e5',
    cryoNameRequired: 'Cryo sample name is required',
    passage: 'Passage',
    passagePlaceholder: 'e.g. P2',
    passageRequired: 'Passage is required, format like P2',
    freezeTime: 'Freeze time',
    freezeTimeRequired: 'Freeze time is required',
    initQty: 'Frozen qty (tubes)',
    initQtyTip: 'This is the INITIAL tube count (it is what the exported "Frozen qty" column shows); lowering it below what has already been withdrawn is rejected by the backend.',
    initQtyRequired: 'Frozen qty must be a positive integer',
    density: 'Density',
    densityPlaceholder: 'e.g. 2e5',
    inMinus80: 'Stored in -80 ultra-low freezer',
    inMinus80Required: 'Please choose yes / no',
    frozenBy: 'Frozen by',
    toLn2Time: 'Moved to liquid nitrogen at',
    ln2Location: 'LN2 location',
    ln2LocationPlaceholder: 'e.g. tank1-rack1-A2',
    ln2LocationRequired: 'LN2 location is required when "no" is chosen or a transfer time is set',
    remark: 'Remark'
  },

  flow: {
    title: 'Stock records',
    subtitle: 'Newest first; "balance after" is computed on read (never stored). Every row can be edited or deleted, and the balance plus the overdue badge refresh immediately.',
    loading: 'Loading…',
    empty: 'No stock record for this batch yet',
    colTime: 'Time',
    colType: 'Type',
    colDelta: 'Change',
    colFrom: 'From',
    colOperator: 'Operator',
    colPurpose: 'Purpose',
    colBalanceAfter: 'Balance after',
    colLastModified: 'Last modified',
    lastModifiedNever: 'never',
    lastModified: 'edited · {name} {time}',
    currentRemaining: 'Current remaining: {qty} tubes',
    close: 'Close',
    edit: 'Edit',
    remove: 'Delete',
    removeConfirm: 'Delete this "{type} {delta}" record? Traceability breaks (soft delete — the row stays in the database).',
    removed: 'Deleted',
    edited: 'Updated',
    removedRejected: 'Delete rejected (it would make a later balance negative): {msg}',
    editRejected: 'Update rejected: {msg}',

    dialogTake: 'Withdraw record',
    dialogAdd: 'Replenish record',
    dialogAdjust: 'Stock-take record',
    dialogEdit: 'Edit record',
    qty: 'Tubes',
    qtyTake: 'Tubes to withdraw',
    qtyAdd: 'Tubes to replenish',
    qtyAdjust: 'Adjustment (non-zero, may be negative)',
    qtyTakeRequired: 'Withdraw qty must be a positive integer',
    qtyAddRequired: 'Replenish qty must be a positive integer',
    qtyAdjustRequired: 'Adjustment must not be zero',
    qtyOverRemaining: 'Cannot withdraw more than the current {qty} tubes',
    purpose: 'Purpose / reason',
    purposePlaceholder: 'e.g. recovery culture',
    purposeRequired: 'Stock-take requires a reason',
    operatorName: 'Operator',
    flowTime: 'Time',
    flowTypeLocked: 'The record type cannot be changed; delete it and register again instead.',
    save: 'Save',
    cancel: 'Cancel',
    saved: 'Recorded',
    editSaved: 'Saved',
    saveFailed: 'Save failed, please retry',
    balanceTipCurrent: 'Current remaining {qty} tubes'
  },

  toLn2: {
    title: 'Register transfer to liquid nitrogen',
    tip: 'After saving, the batch location becomes liquid nitrogen and the overdue badge plus the tab counters refresh immediately.',
    time: 'Transfer time',
    timeRequired: 'Transfer time is required and cannot be earlier than the freeze time',
    location: 'LN2 location',
    locationRequired: 'LN2 location is required',
    save: 'Save',
    cancel: 'Cancel',
    saved: 'Transfer registered'
  },

  msg: {
    sampleFiltered: 'Filtered by sample',
    saveFailed: 'Save failed, please retry',
    loadFailed: 'Failed to load, please retry'
  }
};
