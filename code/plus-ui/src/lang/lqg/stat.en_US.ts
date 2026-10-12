// Operator statistics (CR-20261012-22); key path lqg.stat.*
export default {
  operator: {
    title: 'Operator statistics',
    subtitle: 'How many records each person handled per sheet in a period. Sheets are counted separately and never summed; QC sheets are not counted. Click a number to open the matching list.',
    preset: {
      week: 'This week',
      month: 'This month',
      year: 'This year',
      lastYear: 'Last year',
      all: 'All',
      custom: 'Custom'
    },
    customBegin: 'From (optional)',
    customEnd: 'To (optional)',
    customTo: 'to',
    customHint: 'Only "To" = up to that day; only "From" = from that day on',
    query: 'Search',
    refresh: 'Refresh',
    inverted: 'The start date cannot be later than the end date',
    range: {
      all: 'All time',
      between: '{begin} to {end}',
      until: 'Up to {end}',
      since: 'From {begin}'
    },
    by: 'By {label}',
    colName: 'Name',
    total: 'Total',
    me: 'Me',
    unlinked: 'No account',
    unlinkedTip: 'A name typed by hand earlier, not linked to a staff account, so it is listed separately. Open the list and pick the person again to merge these records.',
    noneTip: 'Records where this field is empty. Open the list to fill it in.',
    empty: 'No records in this period',
    note: {
      tissue: 'Approved records only',
      organoid: 'Approved records only',
      embed: 'Approved blocks only; blocks without a paraffin embedding date appear under "All" only',
      cryo: 'Counted by the person who froze the batch; tubes = sum of frozen quantity (not the current remainder)'
    }
  }
};
