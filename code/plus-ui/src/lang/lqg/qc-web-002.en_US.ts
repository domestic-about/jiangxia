// ============================================================================
// Domain i18n (en_US) · QC domain —— QC-WEB-002 (organoid QC sheet + score sheet)
//
// File name = `<domain>-<ticket>`; the part before '-' is the domain, so these keys
// merge into `lqg.qc.*` (see src/lang/index.ts). Top-level keys must not collide
// with qc.en_US.ts (same-domain merge is shallow); this file only adds
// `organoid` and `score`. Key set must stay identical to qc-web-002.zh_CN.ts.
// ============================================================================

export default {
  organoid: {
    observeTitle: 'Observation condition · images',
    formedTime: 'Time organoids formed',
    growthState: 'Growth state',
    growthDesc: 'Organoid growth detail',
    plannedDrugScreen: 'Planned drug screening',
    feedbackTime: 'Feedback time',
    timePlaceholder: 'yyyy-MM-dd, or type text',
    pickDate: 'Pick a date',
    timeHint: 'Picking a date fills yyyy-MM-dd; you can also type text (e.g. "around day 5")'
  },

  score: {
    title: 'Organoid quality score',
    variableCol: 'Item',
    optionsCol: 'Grade',
    scoreCol: 'Organoid quality score',
    points: '{n} pts',
    total: 'Total',
    totalPending: 'Total appears only after all four items are chosen',
    immediateHint:
      'The scores and total on this page are instant feedback; saving submits only the four grades, and the stored scores are filled in by the backend from the dictionary.',
    persisted: 'Stored (filled in by backend): {items}, total {total}',
    persistedEmpty: 'Stored (filled in by backend): total — (some item is not chosen)',
    preCulture: 'Pre-culture sample score',
    cultureDays: 'Culture days',
    organoidCount: 'Organoid count (measured for drug sensitivity)',
    diameter: 'Organoid diameter'
  }
};
