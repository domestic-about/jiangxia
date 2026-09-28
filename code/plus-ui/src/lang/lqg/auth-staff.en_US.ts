// ============================================================================
// Domain i18n (en_US) · AUTH domain —— AUTH-STAFF-001 (internal staff authorization)
// Keys must mirror auth-staff.zh_CN.ts one-for-one.
// ============================================================================

export default {
  staff: {
    title: 'Internal Staff',
    subtitle: 'Authorize staff by phone number. If the phone already has an account, it is upgraded in place — no duplicate account.',
    refresh: 'Refresh',
    grant: 'Authorize by phone',

    colName: 'Name',
    colPhone: 'Phone',
    colRole: 'Role',
    colWxBound: 'WeChat bound',
    colAction: 'Actions',
    wxBound: 'Bound',
    wxUnbound: 'Not bound',

    roleInternal: 'Internal staff',
    roleAdmin: 'Lab administrator',

    changeRole: 'Change role',
    resetPwd: 'Reset password',
    revoke: 'Revoke',

    dialogTitle: 'Authorize by phone',
    phone: 'Phone',
    phonePlaceholder: 'Enter the 11-digit phone number',
    name: 'Name',
    namePlaceholder: 'Enter the real name',
    password: 'Initial workbench password',
    passwordPlaceholder: 'Enter the initial workbench password',
    upgradeTip: 'This phone has signed in via the mini program; the existing account will be upgraded to internal staff',
    newTip: 'No account for this phone yet; an internal account will be created',
    checking: 'Checking the phone number…',

    roleDialogTitle: 'Change role',
    pwdDialogTitle: 'Reset workbench password',
    newPassword: 'New password',

    revokeConfirm: 'Revoking sends this account back to external and signs it out immediately (account and history are kept). Revoke the internal authorization of "{name}"?',

    phoneRequired: 'Phone number is required',
    phoneInvalid: 'Invalid phone number',
    nameRequired: 'Name is required',
    roleRequired: 'Role is required',
    passwordRequired: 'Password is required',
    passwordMin: 'Password must be at least 6 characters',

    grantUpgraded: 'Existing account upgraded to internal staff',
    grantCreated: 'Internal account created',
    roleChanged: 'Role updated',
    pwdReset: 'Password reset',
    revoked: 'Authorization revoked',
    empty: 'No internal staff yet. Use "Authorize by phone" at the top right.'
  }
};
