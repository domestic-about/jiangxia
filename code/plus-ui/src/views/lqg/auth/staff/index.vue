<template>
  <div class="p-2 lqg-staff">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8" align="middle">
          <el-col :span="1.5">
            <el-button v-hasPermi="['lqg:auth:staff:grant']" type="primary" plain icon="Plus" @click="handleGrant">{{ t('lqg.auth.staff.grant') }}</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['lqg:auth:staff:list']" plain icon="Refresh" @click="getList">{{ t('lqg.auth.staff.refresh') }}</el-button>
          </el-col>
        </el-row>
      </template>

      <el-alert type="info" :closable="false" show-icon class="mb8">
        <span v-html="subtitleHtml"></span>
      </el-alert>

      <el-table v-loading="loading" border :data="staffList" :empty-text="t('lqg.auth.staff.empty')">
        <el-table-column :label="t('lqg.auth.staff.colName')" align="center" prop="name" :show-overflow-tooltip="true" />
        <el-table-column :label="t('lqg.auth.staff.colPhone')" align="center" prop="phone" width="150">
          <template #default="scope">
            <span class="lqg-mono">{{ scope.row.phone }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.auth.staff.colRole')" align="center" width="140">
          <template #default="scope">
            <el-tag :type="scope.row.roleKey === 'lqg_admin' ? 'success' : 'primary'" effect="plain">{{ roleLabel(scope.row.roleKey) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.auth.staff.colWxBound')" align="center" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.wxBound ? 'success' : 'info'" effect="plain">
              {{ scope.row.wxBound ? t('lqg.auth.staff.wxBound') : t('lqg.auth.staff.wxUnbound') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('lqg.auth.staff.colAction')" align="center" width="260" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip :content="t('lqg.auth.staff.changeRole')" placement="top">
              <el-button v-hasPermi="['lqg:auth:staff:edit']" link type="primary" icon="Edit" @click="handleChangeRole(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip :content="t('lqg.auth.staff.resetPwd')" placement="top">
              <el-button v-hasPermi="['lqg:auth:staff:resetPwd']" link type="primary" icon="Key" @click="handleResetPwd(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip :content="t('lqg.auth.staff.revoke')" placement="top">
              <el-button v-hasPermi="['lqg:auth:staff:revoke']" link type="danger" icon="CircleClose" @click="handleRevoke(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 按手机号授权（点蒙层可关：main.ts 已把 ElDialog 的 closeOnClickModal 默认改为 false，
         这里显式写 true 是有意的 —— accept 与 UI:admin.auth.staff 都要求「点蒙层可关」） -->
    <el-dialog v-model="grant.visible" :title="t('lqg.auth.staff.dialogTitle')" width="520px" append-to-body :close-on-click-modal="false">
      <el-form ref="grantFormRef" :model="grant.form" :rules="grant.rules" label-width="120px">
        <el-form-item :label="t('lqg.auth.staff.phone')" prop="phone">
          <el-input v-model="grant.form.phone" :placeholder="t('lqg.auth.staff.phonePlaceholder')" maxlength="11" clearable @blur="handleCheckPhone" />
        </el-form-item>
        <el-form-item :label="t('lqg.auth.staff.name')" prop="name">
          <el-input v-model="grant.form.name" :placeholder="t('lqg.auth.staff.namePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('lqg.auth.staff.colRole')" prop="roleKey">
          <el-select v-model="grant.form.roleKey" style="width: 100%">
            <el-option :label="t('lqg.auth.staff.roleInternal')" value="lqg_internal" />
            <el-option :label="t('lqg.auth.staff.roleAdmin')" value="lqg_admin" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('lqg.auth.staff.password')" prop="password">
          <el-input v-model="grant.form.password" type="password" show-password :placeholder="t('lqg.auth.staff.passwordPlaceholder')" />
        </el-form-item>
        <!-- 只读预检的结果：外部账号 → 明确告诉管理员这是「升级」不是「新建」 -->
        <el-alert v-if="checkTip" :type="checkTipType" :closable="false" show-icon class="mb8" :title="checkTip" />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="grant.submitting" @click="submitGrant">确 定</el-button>
          <el-button @click="grant.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 改角色 -->
    <el-dialog v-model="roleDialog.visible" :title="t('lqg.auth.staff.roleDialogTitle')" width="420px" append-to-body :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item :label="t('lqg.auth.staff.colName')">
          <span>{{ roleDialog.name }}</span>
        </el-form-item>
        <el-form-item :label="t('lqg.auth.staff.colRole')">
          <el-select v-model="roleDialog.roleKey" style="width: 100%">
            <el-option :label="t('lqg.auth.staff.roleInternal')" value="lqg_internal" />
            <el-option :label="t('lqg.auth.staff.roleAdmin')" value="lqg_admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="roleDialog.submitting" @click="submitRole">确 定</el-button>
          <el-button @click="roleDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdDialog.visible" :title="t('lqg.auth.staff.pwdDialogTitle')" width="420px" append-to-body :close-on-click-modal="false">
      <el-form ref="pwdFormRef" :model="pwdDialog.form" :rules="pwdDialog.rules" label-width="100px">
        <el-form-item :label="t('lqg.auth.staff.colName')">
          <span>{{ pwdDialog.name }}</span>
        </el-form-item>
        <el-form-item :label="t('lqg.auth.staff.newPassword')" prop="password">
          <el-input v-model="pwdDialog.form.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="pwdDialog.submitting" @click="submitPwd">确 定</el-button>
          <el-button @click="pwdDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LqgAuthStaff" lang="ts">
import { checkStaff, changeStaffRole, grantStaff, listStaff, resetStaffPwd, revokeStaff } from '@/api/lqg/auth/staff';
import type { StaffCheckVO, StaffGrantForm, StaffMemberVO } from '@/api/lqg/auth/staff';
import { useI18n } from 'vue-i18n';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { t } = useI18n();

const loading = ref(false);
const staffList = ref<StaffMemberVO[]>([]);
const checkTip = ref('');
const checkTipType = ref<'warning' | 'info'>('warning');

// 副标题（i18n 里的 **加粗** 语法在前端自己转义 —— 文案里出现的尖括号也被转掉，避免 XSS）
const subtitleHtml = computed(() =>
  t('lqg.auth.staff.subtitle')
    .replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' })[c] as string)
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
);

const roleLabel = (roleKey: string) => (roleKey === 'lqg_admin' ? t('lqg.auth.staff.roleAdmin') : t('lqg.auth.staff.roleInternal'));

/** 查询列表 */
const getList = async () => {
  loading.value = true;
  try {
    const res = await listStaff();
    staffList.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

// ── 授权弹窗 ────────────────────────────────────────────────────────────────
const grantFormRef = ref<ElFormInstance>();
const grant = reactive<{
  visible: boolean;
  submitting: boolean;
  form: StaffGrantForm;
  rules: ElFormRules;
}>({
  visible: false,
  submitting: false,
  form: { phone: '', name: '', roleKey: 'lqg_internal', password: '' },
  rules: {
    phone: [
      { required: true, message: t('lqg.auth.staff.phoneRequired'), trigger: 'blur' },
      { pattern: /^1[3-9]\d{9}$/, message: t('lqg.auth.staff.phoneInvalid'), trigger: 'blur' }
    ],
    name: [{ required: true, message: t('lqg.auth.staff.nameRequired'), trigger: 'blur' }],
    roleKey: [{ required: true, message: t('lqg.auth.staff.roleRequired'), trigger: 'change' }],
    password: [
      { required: true, message: t('lqg.auth.staff.passwordRequired'), trigger: 'blur' },
      { min: 6, message: t('lqg.auth.staff.passwordMin'), trigger: 'blur' }
    ]
  }
});

const resetGrantForm = () => {
  grant.form = { phone: '', name: '', roleKey: 'lqg_internal', password: '' };
  checkTip.value = '';
  checkTipType.value = 'warning';
  grantFormRef.value?.resetFields();
};

const handleGrant = () => {
  resetGrantForm();
  grant.visible = true;
};

/**
 * 提交前调**只读**预检：这个手机号已经有账号时，弹窗里明确提示「将把原账号升级为内部人员」。
 * 预检失败不挡提交（后端才是判据），只是没有提示。
 */
const handleCheckPhone = async () => {
  const phone = (grant.form.phone || '').trim();
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    checkTip.value = '';
    return;
  }
  try {
    const res = await checkStaff(phone);
    const data: StaffCheckVO | undefined = res.data;
    if (data?.external || data?.exists) {
      checkTip.value = t('lqg.auth.staff.upgradeTip');
      checkTipType.value = 'warning';
      if (data?.name) {
        grant.form.name = grant.form.name || data.name;
      }
    } else {
      checkTip.value = t('lqg.auth.staff.newTip');
      checkTipType.value = 'info';
    }
  } catch {
    checkTip.value = '';
  }
};

const submitGrant = () => {
  grantFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    grant.submitting = true;
    try {
      const res = await grantStaff({ ...grant.form, phone: (grant.form.phone || '').trim() });
      proxy?.$modal.msgSuccess(res.data?.upgraded ? t('lqg.auth.staff.grantUpgraded') : t('lqg.auth.staff.grantCreated'));
      grant.visible = false;
      getList();
    } finally {
      grant.submitting = false;
    }
  });
};

// ── 改角色 ──────────────────────────────────────────────────────────────────
const roleDialog = reactive({ visible: false, submitting: false, userId: '' as string | number, name: '', roleKey: 'lqg_internal' });

const handleChangeRole = (row: StaffMemberVO) => {
  roleDialog.userId = row.userId;
  roleDialog.name = row.name;
  roleDialog.roleKey = row.roleKey;
  roleDialog.visible = true;
};

const submitRole = async () => {
  roleDialog.submitting = true;
  try {
    await changeStaffRole(roleDialog.userId, roleDialog.roleKey);
    proxy?.$modal.msgSuccess(t('lqg.auth.staff.roleChanged'));
    roleDialog.visible = false;
    getList();
  } finally {
    roleDialog.submitting = false;
  }
};

// ── 重置密码 ────────────────────────────────────────────────────────────────
const pwdFormRef = ref<ElFormInstance>();
const pwdDialog = reactive<{ visible: boolean; submitting: boolean; userId: string | number; name: string; form: { password: string }; rules: ElFormRules }>({
  visible: false,
  submitting: false,
  userId: '',
  name: '',
  form: { password: '' },
  rules: {
    password: [
      { required: true, message: t('lqg.auth.staff.passwordRequired'), trigger: 'blur' },
      { min: 6, message: t('lqg.auth.staff.passwordMin'), trigger: 'blur' }
    ]
  }
});

const handleResetPwd = (row: StaffMemberVO) => {
  pwdDialog.userId = row.userId;
  pwdDialog.name = row.name;
  pwdDialog.form.password = '';
  pwdDialog.visible = true;
};

const submitPwd = () => {
  pwdFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) {
      return;
    }
    pwdDialog.submitting = true;
    try {
      await resetStaffPwd(pwdDialog.userId, pwdDialog.form.password);
      proxy?.$modal.msgSuccess(t('lqg.auth.staff.pwdReset'));
      pwdDialog.visible = false;
    } finally {
      pwdDialog.submitting = false;
    }
  });
};

// ── 撤销（二次确认） ────────────────────────────────────────────────────────
const handleRevoke = async (row: StaffMemberVO) => {
  await proxy?.$modal.confirm(t('lqg.auth.staff.revokeConfirm', { name: row.name }));
  await revokeStaff(row.userId);
  proxy?.$modal.msgSuccess(t('lqg.auth.staff.revoked'));
  getList();
};

onMounted(() => {
  getList();
});
</script>

<style scoped lang="scss">
.lqg-staff {
  .lqg-mono {
    font-family: var(--lqg-font-mono);
  }
}
</style>
