<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElSkeleton,
  ElTable,
  ElTableColumn,
} from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import PageHeader from '../components/PageHeader.vue';
import {
  createEmployee,
  getEmployeeStats,
  listEmployees,
  updateEmployee,
  type Employee,
  type EmployeePayload,
  type EmployeeStats,
  type Gender,
} from '../api/employees';

interface EmployeeForm {
  employeeNo: string;
  name: string;
  gender: Gender;
  phone: string;
  email: string;
  department: string;
  position: string;
  hireDate: string;
}

type DialogMode = 'create' | 'edit';

const FIELD_NAMES = [
  'employeeNo',
  'name',
  'gender',
  'phone',
  'email',
  'department',
  'position',
  'hireDate',
] as const;

const router = useRouter();

const pageSizes = [10, 20, 50];
const genderOptions: Array<{ value: Gender; label: string }> = [
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
  { value: 'UNKNOWN', label: '未填' },
];

const keyword = ref('');
const appliedKeyword = ref('');
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const items = ref<Employee[]>([]);
const loading = ref(false);
const loadError = ref<string | null>(null);

const stats = ref<EmployeeStats | null>(null);
const statsLoading = ref(false);
const statsError = ref<string | null>(null);
let statsRequestId = 0;

const dialogVisible = ref(false);
const dialogMode = ref<DialogMode>('create');
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<EmployeeForm>(emptyForm());
const serverErrors = reactive<Record<string, string>>({});

const rules: FormRules<EmployeeForm> = {
  employeeNo: [
    { required: true, whitespace: true, message: '工号不能为空', trigger: 'blur' },
    { max: 32, message: '工号长度不能超过32个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, whitespace: true, message: '姓名不能为空', trigger: 'blur' },
    { max: 64, message: '姓名长度不能超过64个字符', trigger: 'blur' },
  ],
  phone: [{ pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 128, message: '邮箱格式不正确', trigger: 'blur' },
  ],
  department: [{ max: 64, message: '部门长度不能超过64个字符', trigger: 'blur' }],
  position: [{ max: 64, message: '职位长度不能超过64个字符', trigger: 'blur' }],
};

function emptyForm(): EmployeeForm {
  return {
    employeeNo: '',
    name: '',
    gender: 'UNKNOWN',
    phone: '',
    email: '',
    department: '',
    position: '',
    hireDate: '',
  };
}

function genderLabel(value: Gender | null | undefined): string {
  switch (value) {
    case 'MALE':
      return '男';
    case 'FEMALE':
      return '女';
    default:
      return '未填';
  }
}

function display(value: string | null | undefined): string {
  return value && value.trim() !== '' ? value : '-';
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

function toPayload(): EmployeePayload {
  return {
    employeeNo: form.employeeNo.trim(),
    name: form.name.trim(),
    gender: form.gender,
    phone: blankToNull(form.phone),
    email: blankToNull(form.email),
    department: blankToNull(form.department),
    position: blankToNull(form.position),
    hireDate: blankToNull(form.hireDate),
  };
}

function clearServerErrors(): void {
  for (const field of FIELD_NAMES) {
    delete serverErrors[field];
  }
}

function clearServerError(field: (typeof FIELD_NAMES)[number]): void {
  delete serverErrors[field];
}

function applyServerError(message: string): void {
  for (const field of FIELD_NAMES) {
    const prefix = `${field}: `;
    if (message.startsWith(prefix)) {
      serverErrors[field] = message.slice(prefix.length);
      return;
    }
  }
  if (message.includes('工号已存在')) {
    serverErrors.employeeNo = message;
  }
}

async function load(options: { refreshStats?: boolean } = {}): Promise<void> {
  loading.value = true;
  loadError.value = null;
  try {
    const result = await listEmployees({
      page: page.value,
      size: pageSize.value,
      keyword: appliedKeyword.value === '' ? undefined : appliedKeyword.value,
    });
    items.value = result.items;
    total.value = result.total;
    if (options.refreshStats !== false) {
      void loadStats();
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载员工列表失败';
    loadError.value = message;
    items.value = [];
    total.value = 0;
    ElMessage.error(message);
  } finally {
    loading.value = false;
  }
}

async function loadStats(): Promise<void> {
  const requestId = ++statsRequestId;
  statsLoading.value = true;
  statsError.value = null;
  try {
    const result = await getEmployeeStats();
    if (requestId !== statsRequestId) {
      return;
    }
    stats.value = {
      total: result.total ?? 0,
      departments: Array.isArray(result.departments) ? result.departments : [],
    };
  } catch (error) {
    if (requestId !== statsRequestId) {
      return;
    }
    stats.value = null;
    const message = error instanceof Error ? error.message : '加载员工统计失败';
    statsError.value = message;
    ElMessage.error(message);
  } finally {
    if (requestId === statsRequestId) {
      statsLoading.value = false;
    }
  }
}

function search(): void {
  appliedKeyword.value = keyword.value.trim();
  page.value = 1;
  void load();
}

function reset(): void {
  keyword.value = '';
  appliedKeyword.value = '';
  page.value = 1;
  void load();
}

function handleCurrentChange(current: number): void {
  page.value = current;
  void load();
}

function handleSizeChange(size: number): void {
  pageSize.value = size;
  page.value = 1;
  void load();
}

function openCreate(): void {
  dialogMode.value = 'create';
  editingId.value = null;
  Object.assign(form, emptyForm());
  clearServerErrors();
  dialogVisible.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

function openEditFromRow(row: unknown): void {
  openEdit(row as Employee);
}

function openEdit(row: Employee): void {
  dialogMode.value = 'edit';
  editingId.value = row.id;
  Object.assign(form, {
    employeeNo: row.employeeNo ?? '',
    name: row.name ?? '',
    gender: row.gender ?? 'UNKNOWN',
    phone: row.phone ?? '',
    email: row.email ?? '',
    department: row.department ?? '',
    position: row.position ?? '',
    hireDate: row.hireDate ?? '',
  });
  clearServerErrors();
  dialogVisible.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

function handleDialogClosed(): void {
  formRef.value?.clearValidate();
  clearServerErrors();
  submitting.value = false;
}

async function submit(): Promise<void> {
  if (submitting.value) {
    return;
  }
  const instance = formRef.value;
  if (!instance) {
    return;
  }
  const valid = await instance.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitting.value = true;
  clearServerErrors();
  try {
    const payload = toPayload();
    if (dialogMode.value === 'create') {
      await createEmployee(payload);
      ElMessage.success('新增成功');
      dialogVisible.value = false;
      page.value = 1;
      await load();
      return;
    }
    if (editingId.value === null) {
      return;
    }
    await updateEmployee(editingId.value, payload);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await load();
  } catch (error) {
    const message = error instanceof Error ? error.message : '提交失败';
    applyServerError(message);
    ElMessage.error(message);
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  void load({ refreshStats: false });
  void loadStats();
});
</script>

<template>
  <main class="page">
    <PageHeader title="员工管理" subtitle="维护员工基础信息，支持按工号或姓名检索。">
      <template #actions>
        <ElButton @click="router.push({ name: 'home' })">返回首页</ElButton>
      </template>
    </PageHeader>

    <section class="card stats-panel">
      <div v-if="statsLoading" class="stats-state" data-testid="stats-loading">
        <ElSkeleton :rows="1" animated />
      </div>
      <div v-else-if="statsError" class="stats-state">
        <p class="error">{{ statsError }}</p>
        <ElButton size="small" @click="loadStats">重试</ElButton>
      </div>
      <template v-else>
        <div class="stats-grid">
          <div class="stat-card stat-total">
            <span class="stat-label">员工总数</span>
            <span class="stat-value">{{ stats?.total ?? 0 }}</span>
          </div>
          <div
            v-for="item in stats?.departments ?? []"
            :key="item.department"
            class="stat-card"
          >
            <span class="stat-label">{{ item.department }}</span>
            <span class="stat-value">{{ item.count }}</span>
          </div>
        </div>
        <p v-if="(stats?.departments?.length ?? 0) === 0" class="muted stats-empty">
          暂无部门统计数据
        </p>
      </template>
    </section>

    <section class="card">
      <div class="toolbar">
        <ElInput
          v-model="keyword"
          class="toolbar-search"
          placeholder="工号／姓名"
          clearable
          @keyup.enter="search"
        />
        <ElButton type="primary" :loading="loading" @click="search">查询</ElButton>
        <ElButton :disabled="loading" @click="reset">重置</ElButton>
        <span class="toolbar-spacer" />
        <ElButton type="primary" :disabled="loading" @click="openCreate">新增员工</ElButton>
      </div>

      <div v-if="loadError" class="load-error">
        <p class="error">{{ loadError }}</p>
        <ElButton @click="load()">重试</ElButton>
      </div>
      <template v-else>
        <ElTable
          v-loading="loading"
          :data="items"
          border
          stripe
          empty-text="暂无员工数据"
          class="employee-table"
        >
          <ElTableColumn prop="employeeNo" label="工号" min-width="110" />
          <ElTableColumn prop="name" label="姓名" min-width="100" />
          <ElTableColumn label="性别" width="80">
            <template #default="scope">{{ genderLabel(scope.row.gender) }}</template>
          </ElTableColumn>
          <ElTableColumn label="部门" min-width="110">
            <template #default="scope">{{ display(scope.row.department) }}</template>
          </ElTableColumn>
          <ElTableColumn label="职位" min-width="110">
            <template #default="scope">{{ display(scope.row.position) }}</template>
          </ElTableColumn>
          <ElTableColumn label="手机号" min-width="130">
            <template #default="scope">{{ display(scope.row.phone) }}</template>
          </ElTableColumn>
          <ElTableColumn label="邮箱" min-width="180">
            <template #default="scope">{{ display(scope.row.email) }}</template>
          </ElTableColumn>
          <ElTableColumn label="入职日期" min-width="120">
            <template #default="scope">{{ display(scope.row.hireDate) }}</template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="90" fixed="right">
            <template #default="scope">
              <ElButton link type="primary" @click="openEditFromRow(scope.row)">编辑</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>

        <div class="pager">
          <ElPagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="page"
            :page-size="pageSize"
            :page-sizes="pageSizes"
            @current-change="handleCurrentChange"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </section>

    <ElDialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增员工' : '编辑员工'"
      width="520px"
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
      @closed="handleDialogClosed"
    >
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="88px" @submit.prevent>
        <ElFormItem label="工号" prop="employeeNo" :error="serverErrors.employeeNo">
          <ElInput
            v-model="form.employeeNo"
            placeholder="请输入工号"
            @input="clearServerError('employeeNo')"
          />
        </ElFormItem>
        <ElFormItem label="姓名" prop="name" :error="serverErrors.name">
          <ElInput
            v-model="form.name"
            placeholder="请输入姓名"
            @input="clearServerError('name')"
          />
        </ElFormItem>
        <ElFormItem label="性别" prop="gender" :error="serverErrors.gender">
          <ElSelect
            v-model="form.gender"
            placeholder="请选择性别"
            style="width: 100%"
            @change="clearServerError('gender')"
          >
            <ElOption
              v-for="option in genderOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="部门" prop="department" :error="serverErrors.department">
          <ElInput
            v-model="form.department"
            placeholder="请输入部门"
            @input="clearServerError('department')"
          />
        </ElFormItem>
        <ElFormItem label="职位" prop="position" :error="serverErrors.position">
          <ElInput
            v-model="form.position"
            placeholder="请输入职位"
            @input="clearServerError('position')"
          />
        </ElFormItem>
        <ElFormItem label="手机号" prop="phone" :error="serverErrors.phone">
          <ElInput
            v-model="form.phone"
            placeholder="请输入手机号"
            @input="clearServerError('phone')"
          />
        </ElFormItem>
        <ElFormItem label="邮箱" prop="email" :error="serverErrors.email">
          <ElInput
            v-model="form.email"
            placeholder="请输入邮箱"
            @input="clearServerError('email')"
          />
        </ElFormItem>
        <ElFormItem label="入职日期" prop="hireDate" :error="serverErrors.hireDate">
          <ElDatePicker
            v-model="form.hireDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="请选择入职日期"
            style="width: 100%"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton :disabled="submitting" @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="submit">保存</ElButton>
      </template>
    </ElDialog>
  </main>
</template>

<style scoped>
.stats-panel {
  margin-bottom: 20px;
}

.stats-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 128px;
  padding: 14px 18px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: var(--app-bg);
}

.stat-total {
  background: var(--app-surface);
  border-color: var(--app-primary);
}

.stat-label {
  color: var(--app-muted);
  font-size: 13px;
  word-break: break-all;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
}

.stats-state {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
}

.stats-state p {
  margin: 0;
}

.stats-empty {
  margin: 12px 0 0;
  font-size: 13px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.toolbar-search {
  width: 220px;
}

.toolbar-spacer {
  flex: 1 1 auto;
}

.employee-table {
  width: 100%;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.load-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 32px 8px;
}

.load-error p {
  margin: 0;
}

@media (max-width: 640px) {
  .toolbar-search {
    width: 100%;
  }
}
</style>
