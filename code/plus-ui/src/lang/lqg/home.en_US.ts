// ============================================================================
// Domain i18n (en_US) · SYS domain — workbench home (SYS-HOME-001 · UI:admin.home)
// File name 'home' → key path `lqg.home.*` (merged by src/lang/index.ts).
// ============================================================================

export default {
  title: 'Workbench',
  subtitle: 'The five numbers are computed on every request and each card jumps straight to its list (zero is shown too — greyed out means "nothing pending").',
  refresh: 'Refresh',
  loadFailed: 'Could not load the todo numbers: {msg} (showing 0 — this does not mean there is nothing to do)',
  allDone: 'Nothing pending',
  go: 'Open →',

  card: {
    pendingSamples: {
      title: 'Samples to verify',
      hint: 'Tissue and organoid intake records',
      zero: 'No sample waiting for verification',
      go: 'Verify in the sample list →'
    },
    pendingEmbeds: {
      title: 'Paraffin embeds to verify',
      hint: 'Paraffin embed submissions from external users',
      zero: 'No paraffin embed waiting for verification',
      go: 'Verify in the paraffin embed list →'
    },
    cryoOverdue: {
      title: '-80 overdue batches',
      hint: 'Frozen longer than the threshold and not moved to LN2',
      zero: 'No overdue batch',
      go: 'Open cryo management (overdue tab) →'
    },
    pendingExtUsers: {
      title: 'External users to verify',
      hint: 'External users whose unit / group is waiting to be matched',
      zero: 'No external user waiting for verification',
      go: 'Verify in people & units →'
    },
    renderFailed: {
      title: 'Document render failures',
      hint: 'QC documents failed to render — regenerate them on the document page',
      zero: 'No failed document',
      go: 'Open the document console →'
    }
  },

  recent: {
    title: 'Recent submissions',
    subtitle: 'Newest first, at most 10 rows',
    empty: 'No submission yet',
    colSubmitTime: 'Submitted at',
    colSubmitNo: 'Submit no.',
    colSourceUnit: 'Source unit',
    colSubmitSource: 'Internal/External',
    colVerifyStatus: 'Verify status'
  },

  source: {
    internal: 'Internal',
    external: 'External'
  },

  status: {
    pending: 'Pending',
    valid: 'Verified',
    invalid: 'Rejected'
  }
};
