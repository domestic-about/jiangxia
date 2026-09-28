// ============================================================================
// 域内 i18n（en_US）· SAMPLE 域 —— SAMPLE-WEB-001（工作台样本表；CR-20260924-10 起拆成两页）
//
// 键集合必须与 sample.zh_CN.ts 完全一致（SYS-WEB-001 的约定）。
// ============================================================================

export default {
  page: {
    tissue: {
      title: 'Sample records',
      subtitle: 'One row per tissue sample; its paraffin blocks and cryo batches are in the "Paraffin / Cryo" column — click a number to open them. Pending partner submissions are highlighted; click "Verify" to handle them.'
    },
    organoid: {
      title: 'Organoid receipts',
      subtitle: 'One row per organoid receipt; its paraffin blocks and cryo batches are in the "Paraffin / Cryo" column — click a number to open them. Pending partner submissions are highlighted; click "Verify" to handle them.'
    }
  },
  search: 'Search',
  reset: 'Reset',

  filter: {
    sourceUnit: 'Source unit',
    group: 'Group',
    sampleKind: 'Kind',
    submitSource: 'Source',
    verifyStatus: 'Verify status',
    receiveDateRange: 'Receive date',
    receiveDateBegin: 'Receive from',
    receiveDateEnd: 'Receive to',
    tissueType: 'Tissue type',
    organoidType: 'Organoid type',
    internalNo: 'Internal no.',
    operatorName: 'Operator',
    donorName: 'Donor name',
    hospitalNo: 'Hospital no.',
    exactMatch: 'exact',
    all: 'All',
    sourceUnitPlaceholder: 'Pick a source unit',
    groupPlaceholder: 'Pick a group',
    groupPlaceholderNoUnit: 'Pick a source unit first',
    internalNoPlaceholder: 'Internal no. (exact)',
    operatorPlaceholder: 'Operator (fuzzy)',
    tissuePlaceholder: 'Tissue type (fuzzy)',
    organoidPlaceholder: 'Organoid type (fuzzy)',
    donorPlaceholder: 'Donor name (exact)',
    hospitalPlaceholder: 'Hospital no. (exact)'
  },

  toolbar: {
    addTissue: 'New sample record',
    addOrganoid: 'New organoid receipt',
    exportTissue: 'Export sample records',
    exportOrganoid: 'Export organoid receipts',
    exportTissueFile: 'Sample records',
    exportOrganoidFile: 'Organoid receipts',
    exportTissueDone: 'Sample records exported (current filter)',
    exportOrganoidDone: 'Organoid receipts exported (current filter)',
    exportEmpty: 'No sample matches the current filter; no file was generated',
    refresh: 'Refresh'
  },

  col: {
    internalNo: 'Internal no.',
    submitNo: 'Submit no.',
    sourceUnit: 'Source unit',
    sampleKind: 'Kind',
    submitSource: 'Source',
    verifyStatus: 'Verify status',
    donorName: 'Donor name',
    gender: 'Gender',
    age: 'Age',
    hospitalNo: 'Hospital no.',
    tissueType: 'Tissue type',
    organoidType: 'Organoid type',
    passage: 'Passage',
    receiveDate: 'Receive date',
    isFixed: 'Fixed',
    processTime: 'Process time',
    hasQcSheet: 'QC sheet',
    hasViabilityReport: 'Viability report',
    hasPathology: 'Pathology',
    submitterName: 'Submitter',
    groupName: 'Group',
    operatorName: 'Operator',
    hint: 'Slicing / staining',
    updateTime: 'Last modified',
    remark: 'Remark',
    relation: 'Paraffin / Cryo',
    action: 'Actions'
  },

  // ── Slicing / staining hint (SAMPLE-HINT-001 / UI:admin.sample.list.hint) ──
  hint: {
    sectioned: 'Sectioned',
    notSectioned: 'Not sectioned',
    none: '—',
    blockNo: 'Block no.',
    sectionTime: 'Section time',
    empty: 'No block details yet',
    loading: 'Loading…',
    loadFailed: 'Block details failed to load',
    open: 'Open the paraffin embedding page'
  },

  status: {
    pending: 'Pending',
    valid: 'Valid',
    invalid: 'Invalid'
  },
  source: {
    internal: 'Internal',
    external: 'External'
  },
  kind: {
    tissue: 'Tissue',
    organoid: 'Organoid'
  },
  flag: {
    yes: 'Yes',
    no: 'No'
  },
  gender: {
    male: 'Male',
    female: 'Female',
    unknown: 'Unknown'
  },
  relation: {
    blocks: 'Blocks {n}',
    blocksLabel: 'Blocks',
    pending: 'Pending {n}',
    cryo: 'Cryo {n} batches',
    cryoLabel: 'Cryo',
    cryoCount: '{n} batches',
    none: '—',
    add: 'Add',
    openBlocks: 'Paraffin embedding records of this sample',
    openPending: 'Partner submissions of this sample waiting for verification',
    openCryo: 'Cryo batches of this sample',
    addBlocks: 'Add a paraffin embedding record for this sample (sample preselected)',
    addCryo: 'Add a cryo batch for this sample (sample preselected)',
    sampleGone: 'This sample was deleted or cannot be found'
  },
  neverModified: 'Never modified',
  empty: 'No samples match the filters',
  rowAction: {
    edit: 'Edit',
    verify: 'Verify',
    qcDoc: 'QC documents',
    qcDocInvalid: 'QC documents open only for verified valid samples',
    notYet: 'lands in a later task',
    delete: 'Delete',
    deleteConfirm: 'Delete sample "{no}"? (soft delete; the internal no. can be reused)',
    deleted: 'Deleted'
  },

  drawer: {
    addTissue: 'New sample record',
    addOrganoid: 'New organoid receipt',
    edit: 'Edit sample',
    verify: 'Verify sample',
    view: 'Sample detail',
    sectionSubmit: 'Submission info',
    sectionReceive: 'Receiving info',
    lastModified: 'Last modified: {name} · {time}',
    lastModifiedNever: 'Never modified',
    save: 'Save',
    cancel: 'Cancel',
    saveValid: 'Mark valid and save',
    saveInvalid: 'Mark invalid',
    invalidReasonTitle: 'Reason for marking invalid',
    invalidReasonRequired: 'A reason is required',
    invalidReasonPlaceholder: 'Say what is wrong; the submitter can read this',
    more: 'More',
    changeToInvalid: 'Mark invalid',
    hasChildrenTip: 'This sample already has embedding / cryo / QC documents and cannot be marked invalid',
    saved: 'Saved',
    verifiedValid: 'Marked valid',
    verifiedInvalid: 'Marked invalid',
    loadFailed: 'Could not load the sample',
    required: 'Required',
    numberRequired: 'Internal no. is required',
    receiveDateRequired: 'Receive date is required',
    kindRequired: 'Sample kind is required',
    tissueRequired: 'Tissue type is required',
    organoidRequired: 'Organoid type is required',
    passageInvalid: 'Passage must be P followed by digits, e.g. P3',
    internalNoTaken: 'This internal no. is already taken'
  },

  field: {
    submitNo: 'Submit no.',
    sampleKind: 'Kind',
    sourceUnit: 'Source unit',
    sourceUnitName: 'Source unit name',
    sourceUnitPlaceholder: 'Fill the unit name here when there is no option',
    donorName: 'Donor name',
    gender: 'Gender',
    age: 'Age',
    hospitalNo: 'Hospital no.',
    tissueType: 'Tissue type',
    organoidType: 'Organoid type',
    passage: 'Passage',
    passagePlaceholder: 'Optional, e.g. P3',
    hasPathology: 'Pathology',
    receiveDate: 'Receive date',
    internalNo: 'Internal no.',
    isFixed: 'Fixed',
    processTime: 'Process time',
    hasQcSheet: 'QC sheet',
    hasViabilityReport: 'Viability report',
    operatorName: 'Operator',
    remark: 'Remark',
    submitterName: 'Submitter',
    groupName: 'Group',
    verifyStatus: 'Verify status',
    invalidReason: 'Invalid reason',
    createTime: 'Created'
  },

  verifyHintOrganoid:
    'Organoid receipt submitted outside: the submission part verifies source unit, organoid type, passage and remark; the receiving part fills receive date, internal no., process time, viability report and operator.',
  verifyHintTissue: 'Tissue sample submitted outside: receive date and internal no. are required, and the internal no. is unique.',
  allSamples: 'All samples',
  loading: 'Loading…'
};
