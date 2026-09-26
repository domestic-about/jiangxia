// ============================================================================
// Domain i18n (en_US) · QC domain — independent-acceptance fixes (2026-09-23, F2: QC documents)
// Mirrors src/lang/lqg/qc-fix.zh_CN.ts; keep both key sets identical.
// ============================================================================

export default {
  integrity: {
    missingTitle: 'Internal version is missing {n} image(s)',
    missingHint:
      'The external version is not delivered to the submitter. Re-upload these images and complete & sync again; if storage was only temporarily unreachable, click Regenerate once it is back.',
    regenerate: 'Regenerate',
    regenerated: 'Regenerated',
    pending: 'Generating…',
    pendingHint: 'It refreshes by itself when ready; you can also click Refresh later',
    imageSizeTip: 'up to {max} MB per image',
    uploadTooLargeServer: '"{name}" exceeds the size the server accepts ({max} MB per file) and was not uploaded',
    uploadRejected:
      '"{name}" failed to upload: the server rejected the file (HTTP {status}), most likely because it exceeds the size the server accepts. This page allows {max} MB per file; if the file is smaller, ask an administrator to check the server upload limit',
    uploadNetwork: '"{name}" failed to upload: the network dropped or the server did not respond. Please retry',
    uploadBroken:
      '"{name}" failed to upload: the server dropped the connection (HTTP {status}). For large files this usually means the server size limit was exceeded (this page allows {max} MB per file); retry later or contact an administrator',
    uploadExpired: 'Your session has expired. Please sign in again before uploading',
    uploadServerError: '"{name}" failed to upload: {msg}',
    bindFailed: '"{name}" was uploaded but could not be attached to the document: {msg}'
  }
};
