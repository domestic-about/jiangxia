// ============================================================================
// 域内 i18n（en_US）· AUTH 域 —— AUTH-GROUP-001（source units and groups + external user verification）
// Key path = `lqg.auth.*`（文件名 `auth-group.*` 的 '-' 之前是域名）。
// ============================================================================

export default {
  unit: {
    title: 'Source Units and Groups',
    subtitle: 'Units and groups are **never hard-deleted**, only enabled / disabled. A disabled unit or group stops appearing in the mini-app picker; people already bound to it are unaffected.',
    refresh: 'Refresh',
    addUnit: 'New Unit',

    unitPanel: 'Source Units',
    colUnitName: 'Unit Name',
    colGroupCount: 'Groups',
    colUnitStatus: 'Status',
    unitEmpty: 'No source unit yet. Click "New Unit" above.',

    groupPanel: 'Groups of this unit',
    groupPanelHint: '({unit})',
    groupPanelNone: 'Pick a unit on the left first',
    addGroup: 'New Group',
    colGroupName: 'Group Name',
    colVerifiedCount: 'Verified Members',
    colGroupStatus: 'Status',
    groupEmpty: 'This unit has no group yet. Click "New Group" above.',

    statusActive: 'Active',
    statusPending: 'Pending',
    statusDisabled: 'Disabled',
    toggleEnable: 'enable',
    toggleDisable: 'disable',

    unitDialogAdd: 'New Source Unit',
    unitDialogEdit: 'Edit Source Unit',
    unitName: 'Unit Name',
    unitNamePlaceholder: 'Unique across the database',
    remark: 'Remark',
    remarkPlaceholder: 'Optional',

    groupDialogAdd: 'New Group',
    groupDialogEdit: 'Edit Group',
    groupName: 'Group Name',
    groupNamePlaceholder: 'Unique inside one unit',
    groupUnit: 'Unit',

    unitNameRequired: 'Unit name is required',
    groupNameRequired: 'Group name is required',
    unitSaved: 'Unit saved',
    groupSaved: 'Group saved',
    unitEnabled: 'Unit enabled',
    unitDisabled: 'Unit disabled',
    groupEnabled: 'Group enabled',
    groupDisabled: 'Group disabled',
    toggleUnitConfirm: 'Set unit "{name}" to {action}? A disabled unit stops appearing in the mini-app picker; bound people are unaffected.',
    toggleGroupConfirm: 'Set group "{name}" to {action}? A disabled group stops appearing in the mini-app picker; bound people are unaffected.'
  },
  extuser: {
    title: 'External Users',
    subtitle: 'Verify the unit and group an external user picked. For self-typed names you **must** choose "Create new" or "Merge into existing" before approving — only then does he start sharing samples with his group.',
    refresh: 'Refresh',
    filterStatus: 'Status',
    filterUnit: 'Unit',
    filterAll: 'All',

    colName: 'Name',
    colPhone: 'Phone',
    colUnit: 'Unit',
    colGroup: 'Group',
    colStatus: 'Status',
    colSampleCount: 'Samples',
    colLastLogin: 'Last Login',
    colAction: 'Actions',
    selfInputTag: 'self-typed',
    notFilled: 'Not filled',
    neverLogin: 'Never',
    empty: 'No external user matches the filter.',

    statusUnbound: 'Not filled',
    statusPending: 'Pending',
    statusVerified: 'Verified',
    statusRejected: 'Rejected',

    verify: 'Verify',
    regroup: 'Change group',

    dialogTitle: 'Verify External User Group',
    dialogUser: 'External user',
    dialogCurrent: 'Current value',
    approve: 'Approve',
    reject: 'Reject',
    approveHint: 'After approval he shares samples with colleagues of the same unit and group.',
    selfInputNotice: 'His unit / group was self-typed. Choose one before approving:',
    newOption: 'Create new unit and group',
    newOptionHint: 'Create "{unit}" and "{group}" as new active records',
    mergeOption: 'Merge into existing',
    mergeOptionHint: 'Pick an existing unit and group below',
    mergeUnit: 'Unit',
    mergeGroup: 'Group',
    mergeGroupFirst: 'Pick a unit first',
    rejectReason: 'Reject reason',
    rejectReasonPlaceholder: 'Required; the external user can see it',
    groupMismatch: 'The group does not belong to the selected unit',

    rejectReasonRequired: 'Reject reason is required',
    mergeRequired: 'Pick both unit and group for "Merge into existing"',
    approved: 'Approved',
    rejected: 'Rejected',
    regrouped: 'Group changed',
    emptyGroup: 'This unit has no active group. Create one in "Source Units and Groups" first.'
  }
};
