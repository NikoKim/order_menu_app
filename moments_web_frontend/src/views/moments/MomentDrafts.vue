<template>
  <section class="draft-page">
    <header class="page-topbar">
      <button class="icon-button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h1>草稿箱</h1>
      <span class="count">{{ drafts.length ? `${drafts.length} 条` : '' }}</span>
    </header>

    <main class="draft-body">
      <LoadingSpinner v-if="loading" />
      <div v-else-if="drafts.length" class="draft-list">
        <article v-for="item in drafts" :key="item.id" class="draft-row" @click="edit(item)">
          <div class="draft-copy">
            <h2>{{ item.title || '未命名动态' }}</h2>
            <p>{{ item.content || '暂无正文' }}</p>
            <time>{{ formatTime(item.updatedAt || item.createdAt) }}</time>
          </div>
          <img v-if="item.imageUrls?.length" :src="imageUrl(item.imageUrls[0])" alt="" />
          <button class="delete-button" title="删除草稿" aria-label="删除草稿" @click.stop="remove(item)"><Trash2 :size="18" /></button>
        </article>
      </div>
      <EmptyState v-else icon="document" title="暂无草稿" description="未发布的动态会保存在这里" />
    </main>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Trash2 } from 'lucide-vue-next'
import { deleteMoment, getMomentDrafts } from '../../api/moments'
import { imageUrl } from '../../composables/useImage'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import { confirmNotice, notifyError } from '../../services/notice'

const router = useRouter()
const drafts = ref([])
const loading = ref(true)

async function load() {
  try {
    const data = await getMomentDrafts({ page: 1, pageSize: 100 })
    drafts.value = data.records || data || []
  } catch { drafts.value = [] }
  finally { loading.value = false }
}
function edit(item) { router.push(`/customer/moments/new?editId=${item.id}`) }
async function remove(item) {
  if (!await confirmNotice('确认删除这条草稿？')) return
  try { await deleteMoment(item.id); drafts.value = drafts.value.filter(row => row.id !== item.id) } catch (error) { notifyError(error.message) }
}
function formatTime(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
onMounted(load)
</script>

<style scoped>
.draft-page { min-height: 100dvh; background: #f6f7f8; }
.page-topbar { min-height: 60px; position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: 42px 1fr 42px; align-items: center; padding: 8px 10px; background: #fff; border-bottom: 1px solid #eaecf0; }
.page-topbar h1 { text-align: center; font-size: 18px; font-weight: 700; }
.icon-button { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 8px; background: transparent; color: #202124; }
.count { color: #969ba1; font-size: 12px; text-align: right; white-space: nowrap; }
.draft-body { padding: 12px; }
.draft-list { display: flex; flex-direction: column; gap: 10px; }
.draft-row { min-height: 104px; position: relative; display: flex; gap: 12px; padding: 15px 52px 15px 16px; background: #fff; border: 1px solid #eaecf0; border-radius: 8px; cursor: pointer; }
.draft-copy { min-width: 0; flex: 1; }
.draft-copy h2 { margin-bottom: 5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 16px; }
.draft-copy p { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; color: #686d74; font-size: 13px; }
.draft-copy time { display: block; margin-top: 8px; color: #a0a4aa; font-size: 11px; }
.draft-row > img { width: 74px; height: 74px; flex: 0 0 auto; border-radius: 6px; object-fit: cover; }
.delete-button { width: 36px; height: 36px; position: absolute; right: 9px; top: 9px; display: grid; place-items: center; border-radius: 8px; background: transparent; color: #a0a4aa; }
.delete-button:hover { background: #ffebee; color: #e85d4a; }
@media (max-width: 480px) { .page-topbar { padding-top: max(8px, env(safe-area-inset-top)); } .draft-body { padding: 10px; } }
</style>
