<template>
  <el-card>
    <template #header>
      <div class="hd">
        <span>开具电子处方</span>
        <el-tag v-if="form.rxNo" type="info">草稿 {{ form.rxNo }}</el-tag>
      </div>
    </template>

    <el-form :model="form" label-width="92px" style="max-width:900px">
      <el-divider content-position="left">患者信息</el-divider>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="患者ID" required>
            <el-input v-model.number="form.patientId" placeholder="如 4（演示患者）"/>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="姓名" required><el-input v-model="form.patientName"/></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="年龄/性别">
            <el-input-number v-model="form.patientAge" :min="0" :max="150" controls-position="right" style="width:110px"/>
            <el-select v-model="form.patientGender" style="width:90px;margin-left:6px">
              <el-option label="男" :value="1"/><el-option label="女" :value="2"/>
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">诊断（ICD-10）</el-divider>
      <div v-for="(d, i) in form.diagnoses" :key="i" class="line">
        <el-input v-model="d.icd10Code" placeholder="ICD-10 编码" style="width:170px"/>
        <el-input v-model="d.diagnosisName" placeholder="诊断名称（必填）" style="width:380px"/>
        <el-button link type="danger" @click="form.diagnoses.splice(i,1)">删除</el-button>
      </div>
      <el-button size="small" @click="form.diagnoses.push({icd10Code:'',diagnosisName:''})">+ 添加诊断</el-button>

      <el-divider content-position="left">药品明细 Rp</el-divider>
      <el-table :data="form.items" size="small" border>
        <el-table-column label="药品编码" width="150">
          <template #default="{row}"><el-input v-model="row.drugCode" placeholder="DRUG-AMLOD"/></template>
        </el-table-column>
        <el-table-column label="名称/规格" width="220">
          <template #default="{row}">
            <el-input v-model="row.drugName" placeholder="名称"/>
            <el-input v-model="row.spec" placeholder="规格" style="margin-top:4px"/>
          </template>
        </el-table-column>
        <el-table-column label="数量/单位" width="150">
          <template #default="{row}">
            <el-input-number v-model="row.qty" :min="1" :precision="0" controls-position="right" style="width:85px"/>
            <el-input v-model="row.unit" placeholder="盒" style="width:50px"/>
          </template>
        </el-table-column>
        <el-table-column label="单次剂量" width="150">
          <template #default="{row}">
            <el-input v-model="row.singleDose" placeholder="5mg"/>
            <el-input v-model="row.doseUnit" placeholder="片" style="margin-top:4px"/>
          </template>
        </el-table-column>
        <el-table-column label="频次/途径/天数" width="240">
          <template #default="{row}">
            <el-select v-model="row.frequency" placeholder="频次" style="width:80px">
              <el-option v-for="f in ['QD','BID','TID','QOD','PRN']" :key="f" :label="f" :value="f"/>
            </el-select>
            <el-input v-model="row.administrationRoute" placeholder="口服" style="width:70px;margin:0 4px"/>
            <el-input-number v-model="row.days" :min="1" controls-position="right" placeholder="天" style="width:75px"/>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{$index}"><el-button link type="danger" @click="form.items.splice($index,1)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <el-button size="small" style="margin-top:8px" @click="addItem">+ 添加药品</el-button>

      <el-divider/>
      <el-space>
        <el-button @click="save(false)" :loading="saving">暂存草稿</el-button>
        <el-button type="primary" @click="signDialog = true" :disabled="!form.patientId || !form.items.length">
          签名并提交审方
        </el-button>
      </el-space>
    </el-form>

    <el-dialog v-model="signDialog" title="电子签名确认" width="420px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px"
        title="签名即表示对处方内容负责。将使用您的 CA 私钥完成 SHA256withRSA 数字签名。"/>
      <el-input v-model="signPassword" type="password" placeholder="请输入登录密码完成重认证" show-password @keyup.enter="doSubmit"/>
      <template #footer>
        <el-button @click="signDialog=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSubmit">确认签名提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../../api'

const router = useRouter()
const saving = ref(false)
const signDialog = ref(false)
const signPassword = ref('')

const form = reactive({
  patientId: 4, patientName: '王患者', patientAge: 41, patientGender: 1,
  diagnoses: [{ icd10Code: 'I10.x00', diagnosisName: '原发性高血压' }],
  items: []
})

function addItem() {
  form.items.push({ drugCode: 'DRUG-AMLOD', drugName: '苯磺酸氨氯地平片', spec: '5mg*7片', qty: 2, unit: '盒',
    singleDose: '5mg', doseUnit: '片', frequency: 'QD', administrationRoute: '口服', days: 14 })
}
addItem()

function payload() {
  return {
    patientId: form.patientId, patientName: form.patientName, patientAge: form.patientAge,
    patientGender: form.patientGender, deptCode: 'CARD', deptName: '心血管内科', rxCategory: 1,
    diagnoses: form.diagnoses.filter(d => d.diagnosisName),
    items: form.items.map((it, i) => ({ ...it, seq: i + 1 }))
  }
}

async function save(sign) {
  saving.value = true
  try {
    let rxNo = form.rxNo
    if (!rxNo) {
      const d = await api.post('/api/prescriptions', payload())
      rxNo = d.rxNo; form.rxNo = rxNo
      ElMessage.success('草稿已保存：' + rxNo)
    }
    if (sign) {
      await api.post('/api/auth/sign-grant', { password: signPassword.value })
      await api.post(`/api/prescriptions/${rxNo}/submit`, { confirmed: true })
      ElMessage.success('已数字签名并提交审方')
      router.push(`/rx/${rxNo}`)
    }
  } finally { saving.value = false; signDialog.value = false }
}

function doSubmit() { save(true) }
</script>

<style scoped>
.hd { display: flex; justify-content: space-between; align-items: center; }
.line { display: flex; gap: 8px; margin-bottom: 8px; }
</style>
