// ============================================================================
// Domain i18n (en_US) · QC domain —— QC-WEB-001 (workbench QC document editor)
//
// Key set must stay identical to qc.zh_CN.ts (see that file's header for the
// naming rule: a domain file without a ticket suffix default-exports the domain
// object, so keys resolve as `lqg.qc.<key>`).
// ============================================================================

export default {
  uploading: 'Uploading, please wait…',
  uploadFailed: 'Upload failed',

  image: {
    add: 'Upload image',
    tip: '1-{max} images; drag to reorder, click to enlarge',
    broken: 'Image failed to load',
    badType: 'Only jpg / png / tif / tiff / bmp / webp / gif images are supported',
    tooLarge: 'Image must be no larger than {max} MB',
    added: 'Image uploaded',
    removed: 'Image removed',
    removeConfirm: 'Remove this image?'
  },

  attachment: {
    title: 'Attachments',
    add: 'Add attachment',
    empty: 'No attachments yet',
    tip: 'Multiple attachments allowed; each up to 50 MB',
    sizeUnknown: 'size unknown',
    tooLarge: 'Attachment must be no larger than {max} MB',
    added: 'Attachment added',
    removed: 'Attachment removed',
    removeConfirm: 'Remove attachment "{name}"?'
  },

  editor: {
    title: 'QC document',
    readonlyHint: 'The seven fields below come from the sample master record and are read-only; edit them in {name}',
    back: '‹ Back to {name}',
    sourceUnit: 'Source unit',
    donorName: 'Patient name',
    gender: 'Gender',
    receiveDate: 'Received',
    processTime: 'Processed',
    operatorName: 'Operator',
    internalNo: 'Internal no.',

    tabSampleQc: 'Sample QC',
    tabOrganoidQc: 'Organoid QC',
    tabScore: 'Organoid quality score',
    statusDraft: 'Draft',
    statusPublished: 'Completed',
    organoidPlaceholder: 'Editing the organoid QC sheet lands in QC-WEB-002 (placeholder here)',
    scorePlaceholder: 'Editing the organoid quality score sheet lands in QC-WEB-002 (placeholder here)',

    previewTitle: 'Preview',
    previewPlaceholder: 'Save the draft, then click "Preview"',
    previewPlaceholderSub: 'Click "Preview" to generate page images; same artifact as the Word / PDF download',
    renderFailed: 'This document failed to render',
    renderNoReason: '(the backend gave no reason)',
    regenerate: 'Regenerate',
    renderPending: 'Rendering…',
    renderDone: '{pages} page(s) generated',
    regenerated: 'Regenerated',
    renderStillFailed: 'Still failing: ',

    saveDraft: 'Save draft',
    preview: 'Preview',
    publish: 'Complete & sync to submitter',
    footerHint: '"Preview" only looks; the submitter sees the document after "Complete & sync"',

    saved: 'Draft saved',
    loadFailed: 'Failed to load the QC document',
    missingSampleId: 'Missing sampleId in the URL: open this page from the "QC documents" row action',
    unsavedTitle: 'Unsaved changes',
    unsavedMessage: 'This page has unsaved changes that will be lost. Leave anyway?',
    unsavedLeave: 'Leave',
    unsavedStay: 'Stay and edit'
  },

  tab: {
    patientNo: 'Patient no.',
    patientNoPlaceholder: 'e.g. P-0231',
    samplingSite: 'Sampling site',
    samplingMethod: 'Sampling method',
    clinicalDiagnosis: 'Clinical diagnosis / prior treatment',
    receiveDesc: 'Receipt description',
    viability: 'Viability assay (attachment)',
    viabilityAdd: 'Upload viability report',
    viabilityUnnamed: 'Uploaded viability report',
    viabilityReplaceHint: 'Click the file name to replace',
    viabilityRemove: 'Remove',
    viabilityPicked: 'Picked "{name}"; it takes effect after saving the draft',
    origTitle: 'As-received condition · images',
    observeTitle: 'Observation condition · images',
    pretreatTitle: 'Pretreatment condition · images',
    descLabel: 'Description'
  }
};
