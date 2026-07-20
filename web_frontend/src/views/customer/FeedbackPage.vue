<template>
  <div class="page-container feedback-submit-page">
    <div class="page-header"><h1 class="page-title">留言建议</h1><router-link to="/customer/feedback/history" class="history-link"><History :size="17" />反馈记录</router-link></div>
    <section class="intro"><span><MessageCircle :size="22" /></span><div><h2>你的反馈很重要</h2><p>可以选择关联商家，也可以直接给平台留言，我们会持续改进点餐体验。</p></div></section>
    <section class="form-card">
      <label class="form-group"><span class="form-label">关联商家</span><select v-model="shopId" class="form-select"><option value="">不指定商家</option><option v-for="shop in shops" :key="shop.id" :value="shop.id">{{ shop.name }}</option></select></label>
      <div class="form-group"><span class="form-label">反馈类型</span><div class="segments"><button v-for="item in ['建议','问题','表扬']" :key="item" :class="{ active: type === item }" @click="type = item">{{ item }}</button></div></div>
      <label class="form-group"><span class="form-label">留言内容</span><textarea v-model.trim="content" class="form-textarea" maxlength="1000" rows="6" placeholder="例如：希望增加米饭选项，或者某道菜很好吃。"></textarea><small>{{ content.length }}/1000</small></label>
      <p v-if="notice" :class="{ error: failed }">{{ notice }}</p>
      <button class="btn btn-primary btn-block btn-lg" :disabled="submitting || !content" @click="submit"><Send :size="18" />{{ submitting ? '提交中...' : '提交' }}</button>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { History, MessageCircle, Send } from 'lucide-vue-next'
import { getMyFamilies, getShops, submitFeedback } from '../../api/customer'
const shops = ref([]), shopId = ref(''), type = ref('建议'), content = ref(''), submitting = ref(false), notice = ref(''), failed = ref(false)
async function loadShops() { const byId = new Map(); try { const data = await getShops({ page:1, pageSize:100 }); (data.records || []).forEach(s => byId.set(s.id, s)) } catch {} try { const data = await getMyFamilies(); (data.families || []).filter(f => f.memberStatus === 'APPROVED').forEach(f => byId.set(f.shopId, { id:f.shopId, name:f.shopName || f.familyName })) } catch {} shops.value = [...byId.values()] }
async function submit() { submitting.value = true; notice.value = ''; failed.value = false; try { await submitFeedback({ shopId: shopId.value || null, content: `[${type.value}] ${content.value}` }); content.value = ''; notice.value = `已提交${type.value}，感谢反馈` } catch (e) { failed.value = true; notice.value = e.message } finally { submitting.value = false } }
onMounted(loadShops)
</script>

<style scoped>
.feedback-submit-page { max-width:720px; }.history-link { min-height:40px; padding:7px 10px; display:inline-flex; align-items:center; gap:5px; font-size:13px; font-weight:700; }.intro,.form-card { margin-bottom:12px; padding:16px; background:#fff; border:1px solid var(--color-line-light); border-radius:var(--radius-lg); }.intro { display:flex; align-items:flex-start; gap:12px; }.intro>span { width:42px; height:42px; flex:0 0 auto; display:inline-flex; align-items:center; justify-content:center; color:var(--color-primary); background:var(--color-primary-bg); border-radius:var(--radius); }.intro h2 { font-size:18px; }.intro p { margin-top:4px; color:var(--color-text-muted); font-size:13px; line-height:1.4; }.form-card { display:flex; flex-direction:column; gap:14px; }.segments { display:grid; grid-template-columns:repeat(3,1fr); border:1px solid var(--color-line); border-radius:var(--radius); overflow:hidden; }.segments button { min-height:42px; color:var(--color-text); background:#fff; }.segments button+button { border-left:1px solid var(--color-line); }.segments button.active { color:#fff; background:var(--color-primary); }.form-group small { text-align:right; color:var(--color-text-muted); }.form-card>p { color:var(--color-success); font-size:13px; }.form-card>p.error { color:var(--color-error); }
</style>
