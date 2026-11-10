<template>
  <div class="${className}-page">
    <!-- 1. 搜索筛选卡片 -->
    <el-card shadow="never" class="search-card">
      <el-form ref="queryFormRef" :model="queryForm" :inline="true" label-width="90px">
<#list list as column>
<#if !column.primaryKey?? || !column.primaryKey>
        <el-form-item label="${(column.columnComment?has_content)?then(column.columnComment, column.columnName)}" prop="${column.columnName}">
<#if column.dataType == 'LocalDateTime'>
          <el-date-picker
            v-model="queryForm.${column.columnName}"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择${(column.columnComment?has_content)?then(column.columnComment, column.columnName)}"
            clearable
          />
<#elseif column.dataType == 'Boolean'>
          <el-select v-model="queryForm.${column.columnName}" placeholder="请选择" clearable>
            <el-option label="是" :value="true" />
            <el-option label="否" :value="false" />
          </el-select>
<#else>
          <el-input
            v-model="queryForm.${column.columnName}"
            placeholder="请输入${(column.columnComment?has_content)?then(column.columnComment, column.columnName)}"
            clearable
            @keyup.enter="handleSearch"
          />
</#if>
        </el-form-item>
</#if>
</#list>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleOpenAdd">新增${functionName}</el-button>
<#if excelEnabled?? && excelEnabled>
          <el-button type="warning" @click="handleExportExcel">导出 Excel</el-button>
</#if>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 2. 数据表格卡片 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="${treeIdColumnName!'id'}"
<#if treeTable?? && treeTable>
        :tree-props="{ children: 'children' }"
        default-expand-all
</#if>
        border
        stripe
      >
<#list list as column>
        <el-table-column
          prop="${column.columnName}"
          label="${(column.columnComment?has_content)?then(column.columnComment, column.columnName)}"
          min-width="120"
          show-overflow-tooltip
        />
</#list>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleOpenEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

<#if !(treeTable?? && treeTable)>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
</#if>
    </el-card>

    <!-- 3. 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑${functionName}' : '新增${functionName}'"
      width="680px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="110px">
<#list list as column>
<#if !column.primaryKey?? || !column.primaryKey>
        <el-form-item label="${(column.columnComment?has_content)?then(column.columnComment, column.columnName)}" prop="${column.columnName}">
<#if column.dataType == 'LocalDateTime'>
          <el-date-picker
            v-model="formModel.${column.columnName}"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择${(column.columnComment?has_content)?then(column.columnComment, column.columnName)}"
          />
<#elseif column.dataType == 'Integer' || column.dataType == 'Long' || column.dataType == 'Short' || column.dataType == 'BigDecimal' || column.dataType == 'Double' || column.dataType == 'Float'>
          <el-input-number v-model="formModel.${column.columnName}" :controls="false" style="width: 100%" />
<#elseif column.dataType == 'Boolean'>
          <el-switch v-model="formModel.${column.columnName}" />
<#else>
          <el-input
            v-model="formModel.${column.columnName}"
<#if column.maxLength?? && (column.maxLength > 0)>
            maxlength="${column.maxLength?c}"
            show-word-limit
</#if>
            placeholder="请输入${(column.columnComment?has_content)?then(column.columnComment, column.columnName)}"
          />
</#if>
        </el-form-item>
</#if>
</#list>
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>

        <!-- 主子表联表编辑：${rel.relationComment} -->
        <el-divider content-position="left">${rel.relationComment}</el-divider>
        <div class="sub-table-toolbar">
          <el-button type="primary" plain size="small" @click="handleAdd${rel.subClassName}Row">
            + 新增${rel.relationComment}
          </el-button>
        </div>
        <el-table :data="formModel.${rel.subClassVarName}List || []" border size="small">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="handleRemove${rel.subClassName}Row($index)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
</#list>
</#if>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import {
  type ${ClassName}VO,
  type ${ClassName}BO,
  type ${ClassName}Query,
<#if treeTable?? && treeTable>
  select${ClassName}Tree,
<#else>
  select${ClassName}Page,
</#if>
  save${ClassName},
  update${ClassName},
  remove${ClassName},
<#if excelEnabled?? && excelEnabled>
  export${ClassName}Excel,
</#if>
} from '../${className}.api';

const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const isEdit = ref(false);
const tableData = ref<${ClassName}VO[]>([]);

const queryFormRef = ref<FormInstance>();
const formRef = ref<FormInstance>();

const queryForm = reactive<${ClassName}Query>({});

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0,
});

const formModel = reactive<${ClassName}BO>({
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
  ${rel.subClassVarName}List: [],
</#list>
</#if>
});

const formRules = reactive<FormRules>({
<#list list as column>
<#if (!column.primaryKey?? || !column.primaryKey) && column.nullable?? && !column.nullable>
  ${column.columnName}: [
    { required: true, message: '${(column.columnComment?has_content)?then(column.columnComment, column.columnName)}不能为空', trigger: 'blur' },
  ],
</#if>
</#list>
});

async function fetchData() {
  loading.value = true;
  try {
<#if treeTable?? && treeTable>
    const res: any = await select${ClassName}Tree(queryForm);
    tableData.value = Array.isArray(res) ? res : (res?.data || []);
<#else>
    const res: any = await select${ClassName}Page(queryForm, pagination.current, pagination.size);
    const pagePayload = res?.records ? res : (res?.data || {});
    tableData.value = pagePayload.records || [];
    pagination.total = Number(pagePayload.total || 0);
</#if>
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pagination.current = 1;
  fetchData();
}

function handleReset() {
  queryFormRef.value?.resetFields();
  handleSearch();
}

function handleOpenAdd() {
  isEdit.value = false;
  Object.keys(formModel).forEach((key) => delete (formModel as any)[key]);
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>
  formModel.${rel.subClassVarName}List = [];
</#list>
</#if>
  dialogVisible.value = true;
}

function handleOpenEdit(row: ${ClassName}VO) {
  isEdit.value = true;
  Object.assign(formModel, row);
  dialogVisible.value = true;
}

async function handleSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate();
  submitting.value = true;
  try {
    if (isEdit.value) {
      await update${ClassName}(formModel);
      ElMessage.success('更新成功');
    } else {
      await save${ClassName}(formModel);
      ElMessage.success('新增成功');
    }
    dialogVisible.value = false;
    fetchData();
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: ${ClassName}VO) {
  await ElMessageBox.confirm('确认删除该条${functionName}记录吗？', '删除确认', { type: 'warning' });
  await remove${ClassName}({ ${treeIdColumnName!'id'}: row.${treeIdColumnName!'id'} } as ${ClassName}BO);
  ElMessage.success('删除成功');
  fetchData();
}
<#if excelEnabled?? && excelEnabled>

async function handleExportExcel() {
  await export${ClassName}Excel(queryForm);
  ElMessage.success('已触发 Excel 导出');
}
</#if>
<#if subTableRelations?? && (subTableRelations?size > 0)>
<#list subTableRelations as rel>

function handleAdd${rel.subClassName}Row() {
  if (!formModel.${rel.subClassVarName}List) {
    formModel.${rel.subClassVarName}List = [];
  }
  formModel.${rel.subClassVarName}List.push({} as any);
}

function handleRemove${rel.subClassName}Row(index: number) {
  formModel.${rel.subClassVarName}List?.splice(index, 1);
}
</#list>
</#if>

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.${className}-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.sub-table-toolbar {
  margin-bottom: 8px;
}
</style>
