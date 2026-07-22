<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">{{ editId ? '编辑动态' : '发布动态' }}</h1>
    </div>

    <div class="editor-card">
      <div class="form-group">
        <input v-model="form.title" class="form-input title-input" minlength="3" maxlength="120" required placeholder="添加标题（至少3个字）" />
      </div>
      <div class="form-group">
        <textarea ref="contentEl" v-model="form.content" class="form-textarea content-area" rows="6" maxlength="20000" placeholder="分享你的想法，输入 @ 提及用户..." @input="handleContentInput"></textarea>
      </div>

      <div class="form-group">
        <MomentQuotePicker v-model:selected="form.quote" :exclude-moment-id="editId" />
      </div>

      <!-- Images -->
      <div class="form-group">
        <div class="editor-images">
          <div v-for="(img, i) in form.imageUrls" :key="`saved-${img}-${i}`" class="editor-img-wrap">
            <img :src="img.startsWith('blob:') ? img : imageUrl(img)" alt="" />
            <button class="editor-img-rm" title="移除图片" @click="form.imageUrls.splice(i, 1)"><X :size="15" /></button>
          </div>
          <div v-for="image in pendingImages" :key="image.id" class="editor-img-wrap" :class="`is-${image.status}`">
            <img :src="image.preview" alt="待上传的动态图片" />
            <div v-if="image.status === 'queued'" class="editor-img-status queued"><span>发布后上传</span></div>
            <div v-if="image.status === 'uploading'" class="editor-img-status"><LoaderCircle :size="21" class="spin" /><span>上传中</span></div>
            <div v-else-if="image.status === 'failed'" class="editor-img-status failed">
              <span>上传失败</span>
              <button title="重新上传" @click="retryImage(image)"><RefreshCw :size="17" /></button>
            </div>
            <button class="editor-img-rm" title="移除图片" @click="removePendingImage(image)"><X :size="15" /></button>
          </div>
          <label v-if="imageCount < 9" class="editor-img-add">
            <ImagePlus :size="25" />
            <input type="file" accept="image/*" multiple hidden @change="handleImageUpload" />
          </label>
        </div>
        <p class="editor-hint">{{ imageCount }}/9 · 选择后保留预览，保存动态后统一上传</p>
      </div>

      <!-- Topics -->
      <div class="form-group topic-section">
        <div class="topic-heading">
          <span class="form-label topic-label"><Hash :size="17" />话题</span>
          <span class="topic-count">{{ form.topics.length }}/5</span>
        </div>
        <div v-if="form.topics.length" class="topic-tags">
          <span v-for="t in form.topics" :key="t" class="topic-tag">
            #{{ t }}
            <button class="topic-tag-rm" :title="`移除话题 ${t}`" @click="removeTopic(t)"><X :size="13" /></button>
          </span>
        </div>
        <div class="topic-input-row">
          <span class="topic-prefix" aria-hidden="true">#</span>
          <input
            v-model="topicInput"
            class="form-input topic-input"
            maxlength="51"
            placeholder="搜索或创建话题"
            autocomplete="off"
            @keyup.enter.prevent="addTopic"
            @input="handleTopicInput"
          />
          <button class="topic-add-btn" title="添加话题" :disabled="topicAdding || !topicQuery" @click="addTopic">
            <LoaderCircle v-if="topicAdding" :size="20" class="spin" />
            <PlusCircle v-else :size="20" />
          </button>
        </div>
        <div v-if="topicQuery" class="topic-discovery">
          <div class="topic-subheading">
            <span>搜索结果</span>
            <span v-if="topicSearching" class="topic-loading-copy"><LoaderCircle :size="14" class="spin" />正在匹配</span>
          </div>
          <div v-if="topicSearching" class="topic-skeletons" aria-label="正在搜索话题">
            <span v-for="i in 5" :key="i" class="topic-skeleton"></span>
          </div>
          <div v-else-if="topicSuggestions.length" class="topic-suggestions">
            <button v-for="t in topicSuggestions" :key="t.id || t.name" class="topic-sug-item" @click="selectTopic(t)">
              <Hash :size="14" />
              <span>{{ t.name }}</span>
              <small>{{ topicCount(t) }}</small>
            </button>
          </div>
          <button v-else class="topic-create-hint" @click="addTopic">
            暂无匹配话题，创建 <strong>#{{ topicQuery }}</strong>
          </button>
        </div>
        <div v-else class="topic-discovery">
          <div class="topic-subheading">
            <span>热门话题</span>
            <button v-if="hotTotal > 10" class="topic-refresh" :disabled="hotLoading" @click="loadHotTopics(true)">
              <RefreshCw :size="14" :class="{ spin: hotLoading }" />换一换
            </button>
          </div>
          <div v-if="hotLoading && !hotTopics.length" class="topic-skeletons" aria-label="正在加载热门话题">
            <span v-for="i in 5" :key="i" class="topic-skeleton"></span>
          </div>
          <div v-else-if="hotTopics.length" class="topic-suggestions">
            <button v-for="t in hotTopics" :key="t.id || t.name" class="topic-sug-item" @click="selectTopic(t)">
              <Hash :size="14" />
              <span>{{ t.name }}</span>
              <small>{{ topicCount(t) }}</small>
            </button>
          </div>
          <button v-else-if="!hotLoading" class="topic-retry" @click="loadHotTopics(false)">重新加载热门话题</button>
        </div>
      </div>

      <!-- Visibility -->
      <div class="form-group">
        <label class="form-label">谁可以看到</label>
        <div class="vis-options">
          <label v-for="opt in visOpts" :key="opt.value" class="vis-opt" :class="{ active: form.visibility === opt.value }">
            <input type="radio" v-model="form.visibility" :value="opt.value" hidden />
            <component :is="opt.icon" class="vis-opt-icon" :size="20" />
            <span class="vis-opt-copy"><span class="vis-opt-label">{{ opt.label }}</span><span class="vis-opt-desc">{{ opt.desc }}</span></span>
          </label>
        </div>
      </div>

      <p v-if="error" class="form-error">{{ error }}</p>
      <div class="editor-buttons">
        <button class="btn-draft" @click="saveDraft" :disabled="saving"><Save :size="18" />{{ saving ? '保存中...' : '存草稿' }}</button>
        <button class="btn-publish" @click="publish" :disabled="saving"><Send :size="18" />{{ publishButtonText }}</button>
      </div>
    </div>
    <MentionPicker :visible="mentionPickerOpen" :selected-ids="mentionUsers.map(user => user.id)" @select="selectMentionUser" @close="mentionPickerOpen = false" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Globe2, Hash, ImagePlus, LoaderCircle, LockKeyhole, PlusCircle, RefreshCw, Save, Send, UsersRound, X } from 'lucide-vue-next'
import { createMoment, updateMoment, getMomentComment, getMomentDetail, uploadMomentImage, searchTopics as fetchTopics, createTopic } from '../../api/moments'
import { imageUrl } from '../../composables/useImage'
import { notifyError, notifySuccess } from '../../services/notice'
import MentionPicker from '../../components/MentionPicker.vue'
import MomentQuotePicker from '../../components/MomentQuotePicker.vue'

const route = useRoute()
const router = useRouter()
const editId = ref(route.query.editId ? Number(route.query.editId) : null)

const form = ref({
  title: '', content: '', visibility: 'PUBLIC', topics: [], imageUrls: [], quote: null
})
const contentEl = ref(null)
const mentionUsers = ref([])
const mentionPickerOpen = ref(false)

const visOpts = [
  { value: 'PUBLIC', label: '公开', desc: '所有人可见', icon: Globe2 },
  { value: 'RELATED', label: '相关', desc: '好友和家人可见', icon: UsersRound },
  { value: 'PRIVATE', label: '私密', desc: '仅自己可见', icon: LockKeyhole }
]

const topicInput = ref('')
const topicSuggestions = ref([])
const hotTopics = ref([])
const hotPage = ref(1)
const hotHasMore = ref(true)
const hotTotal = ref(0)
const hotLoading = ref(false)
const topicSearching = ref(false)
const topicAdding = ref(false)
const pendingImages = ref([])
const saving = ref(false)
const error = ref('')
const savedStatus = ref('')
const topicQuery = computed(() => topicInput.value.trim().replace(/^#+/, '').trim())
const imageCount = computed(() => form.value.imageUrls.length + pendingImages.value.length)
const publishButtonText = computed(() => {
  if (!saving.value) return pendingImages.value.some(image => image.status === 'failed') ? '重试并完成' : '发布动态'
  return pendingImages.value.some(image => image.status === 'uploading') ? '正在上传图片...' : '正在发布...'
})
let topicDebounce = null
let topicSearchSequence = 0
let pendingImageId = 0

async function handleImageUpload(e) {
  const files = [...(e.target.files || [])]
  if (!files.length) return
  error.value = ''
  const available = Math.max(0, 9 - imageCount.value)
  if (files.length > available) notifyError('最多添加9张图片')
  const added = files.slice(0, available).map(file => ({
    id: ++pendingImageId,
    file,
    preview: URL.createObjectURL(file),
    remoteUrl: '',
    status: 'queued',
    error: ''
  }))
  pendingImages.value.push(...added)
  e.target.value = ''
}

async function uploadPendingImage(image) {
  const imageId = image.id
  const file = image.file
  updatePendingImage(imageId, { status: 'uploading', error: '' })
  try {
    const result = await uploadMomentImage(file)
    if (!pendingImages.value.some(item => item.id === imageId)) return false
    if (!result?.url) throw new Error('服务器未返回图片地址')
    updatePendingImage(imageId, { remoteUrl: result.url, status: 'done' })
    return true
  } catch (err) {
    if (!pendingImages.value.some(item => item.id === imageId)) return false
    updatePendingImage(imageId, { status: 'failed', error: err.message || '上传失败' })
    return false
  }
}

function updatePendingImage(id, patch) {
  const index = pendingImages.value.findIndex(item => item.id === id)
  if (index >= 0) pendingImages.value[index] = { ...pendingImages.value[index], ...patch }
}

async function retryImage(image) {
  if (!editId.value) {
    updatePendingImage(image.id, { status: 'queued', error: '' })
    return
  }
  saving.value = true
  try {
    const uploaded = await uploadPendingImage(image)
    if (!uploaded) return
    await updateMoment(editId.value, momentPayload(savedStatus.value || 'PUBLISHED', uploadedImageUrls()))
    notifySuccess('图片上传成功')
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

function removePendingImage(image) {
  pendingImages.value = pendingImages.value.filter(item => item.id !== image.id)
  URL.revokeObjectURL(image.preview)
}

function uploadedImageUrls() {
  return [
    ...form.value.imageUrls,
    ...pendingImages.value.filter(image => image.status === 'done' && image.remoteUrl).map(image => image.remoteUrl)
  ]
}

function momentPayload(status, imageUrls) {
  const quote = form.value.quote
  return {
    title: form.value.title.trim(),
    content: form.value.content.trim(),
    visibility: form.value.visibility,
    status,
    topics: form.value.topics,
    imageUrls,
    quotedMomentId: quote?.type === 'MOMENT' ? quote.momentId : null,
    quotedCommentId: quote?.type === 'COMMENT' ? quote.commentId : null,
    mentionedUserIds: activeMentions().map(user => Number(user.id))
  }
}

function handleContentInput(event) {
  const input = event?.target
  const cursor = input?.selectionStart ?? form.value.content.length
  if (form.value.content.slice(0, cursor).endsWith('@')) mentionPickerOpen.value = true
}

function selectMentionUser(user) {
  const input = contentEl.value
  const cursor = input?.selectionStart ?? form.value.content.length
  const before = form.value.content.slice(0, cursor)
  const after = form.value.content.slice(cursor)
  const replaceAt = before.endsWith('@') ? before.slice(0, -1) : before
  form.value.content = `${replaceAt}@${user.nickname} ${after}`
  if (!mentionUsers.value.some(item => Number(item.id) === Number(user.id))) mentionUsers.value.push(user)
  mentionPickerOpen.value = false
  nextTick(() => {
    const nextCursor = replaceAt.length + user.nickname.length + 2
    input?.focus()
    input?.setSelectionRange(nextCursor, nextCursor)
  })
}

function activeMentions() {
  return mentionUsers.value.filter(user => form.value.content.includes(`@${user.nickname}`))
}

function topicPage(result) {
  if (Array.isArray(result)) return { records: result, total: result.length, hasMore: false }
  return {
    records: result?.records || result?.items || [],
    total: Number(result?.total || 0),
    hasMore: Boolean(result?.hasMore)
  }
}

function topicCount(topic) {
  return Number(topic?.momentCount ?? topic?.usageCount ?? 0)
}

function sameTopic(left, right) {
  return String(left).trim().toLocaleLowerCase() === String(right).trim().toLocaleLowerCase()
}

function handleTopicInput() {
  clearTimeout(topicDebounce)
  if (/^#+/.test(topicInput.value)) topicInput.value = topicInput.value.replace(/^#+/, '')
  const query = topicQuery.value
  const sequence = ++topicSearchSequence
  if (!query) {
    topicSuggestions.value = []
    topicSearching.value = false
    return
  }
  topicSearching.value = true
  topicDebounce = setTimeout(async () => {
    try {
      const result = topicPage(await fetchTopics({ query, page: 1, pageSize: 10 }))
      if (sequence === topicSearchSequence) topicSuggestions.value = result.records
    } catch {
      if (sequence === topicSearchSequence) topicSuggestions.value = []
    } finally {
      if (sequence === topicSearchSequence) topicSearching.value = false
    }
  }, 300)
}

async function loadHotTopics(next = false) {
  if (hotLoading.value) return
  hotLoading.value = true
  const requestedPage = next ? (hotHasMore.value ? hotPage.value + 1 : 1) : 1
  try {
    const result = topicPage(await fetchTopics({ page: requestedPage, pageSize: 10 }))
    hotTopics.value = result.records
    hotPage.value = requestedPage
    hotHasMore.value = result.hasMore
    hotTotal.value = result.total
  } catch {
    if (!next) hotTopics.value = []
  } finally {
    hotLoading.value = false
  }
}

function canSelectTopic(name, showNotice = true) {
  if (form.value.topics.length >= 5) {
    if (showNotice) notifyError('最多添加5个话题')
    return false
  }
  if (form.value.topics.some(topic => sameTopic(topic, name))) return false
  return true
}

function commitTopic(topic) {
  const name = String(topic?.name || '').trim().replace(/^#+/, '')
  if (!name || !canSelectTopic(name)) return false
  form.value.topics.push(name)
  topicInput.value = ''
  topicSuggestions.value = []
  topicSearching.value = false
  topicSearchSequence += 1
  return true
}

async function addTopic() {
  const name = topicQuery.value
  if (!name || !canSelectTopic(name)) return
  const existing = topicSuggestions.value.find(topic => sameTopic(topic.name, name))
  if (existing) {
    commitTopic(existing)
    return
  }
  topicAdding.value = true
  try {
    commitTopic(await createTopic(name))
  } catch (e) {
    notifyError(e.message || '话题创建失败')
  } finally {
    topicAdding.value = false
  }
}

function selectTopic(t) {
  commitTopic(t)
}

function removeTopic(name) {
  form.value.topics = form.value.topics.filter(topic => !sameTopic(topic, name))
}

async function publish() {
  error.value = ''
  if (form.value.title.trim().length < 3) {
    error.value = '标题至少需要3个字'
    return
  }
  if (!form.value.content.trim()) {
    error.value = '内容不能为空'
    return
  }
  await saveMoment('PUBLISHED')
}

async function saveDraft() {
  error.value = ''
  await saveMoment('DRAFT')
}

async function saveMoment(status) {
  saving.value = true
  try {
    const initialPayload = momentPayload(status, uploadedImageUrls())
    if (editId.value) {
      await updateMoment(editId.value, initialPayload)
    } else {
      const created = await createMoment(initialPayload)
      if (!created?.id) throw new Error('动态保存成功，但未返回动态编号')
      editId.value = Number(created.id)
    }
    savedStatus.value = status

    const waitingImages = pendingImages.value.filter(image => image.status === 'queued' || image.status === 'failed')
    if (waitingImages.length) {
      await Promise.all(waitingImages.map(uploadPendingImage))
      await updateMoment(editId.value, momentPayload(status, uploadedImageUrls()))
    }

    const failedCount = pendingImages.value.filter(image => image.status === 'failed').length
    if (failedCount) {
      notifyError(`动态已${status === 'DRAFT' ? '保存' : '发布'}，${failedCount}张图片上传失败，可在当前页面重试`)
      return
    }
    router.back()
  } catch (e) { error.value = e.message }
  finally { saving.value = false }
}

onMounted(async () => {
  loadHotTopics()
  if (editId.value) {
    try {
      const m = await getMomentDetail(editId.value)
      form.value = {
        title: m.title || '', content: m.content || '',
        visibility: m.visibility || 'PUBLIC',
        topics: (m.topics || []).map(t => t.name),
        imageUrls: m.imageUrls || [],
        quote: m.quote || null
      }
      mentionUsers.value = m.mentions || []
      savedStatus.value = m.status || 'PUBLISHED'
    } catch { }
  }
  await loadRequestedQuote()
})

async function loadRequestedQuote() {
  const momentId = Number(route.query.quotedMomentId)
  const commentId = Number(route.query.quotedCommentId)
  if (!momentId) return
  try {
    const source = await getMomentDetail(momentId)
    if (commentId) {
      const comment = await getMomentComment(momentId, commentId)
      form.value.quote = {
        type: 'COMMENT', momentId, commentId,
        author: comment.author, momentTitle: source.title, content: comment.content,
        createdAt: comment.createdAt, available: true
      }
      return
    }
    form.value.quote = {
      type: 'MOMENT', momentId, commentId: null,
      author: source.author, momentTitle: source.title, content: source.content,
      createdAt: source.publishedAt || source.createdAt, available: true
    }
  } catch (e) {
    notifyError(e.message || '引用内容不可用')
  }
}

onBeforeUnmount(() => {
  clearTimeout(topicDebounce)
  pendingImages.value.forEach(image => URL.revokeObjectURL(image.preview))
})
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; justify-content: flex-start; }
.back-btn {
  width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center;
  border-radius: 50%; background: transparent; color: var(--color-text);
}
.page-title { font-size: 20px; font-weight: 700; }

.editor-card { background: #fff; border-radius: var(--radius-xl); padding: 20px; }
.editor-card .form-group + .form-group { margin-top: 10px; }

.title-input {
  font-size: 18px; font-weight: 700; padding: 12px 14px;
  border: 1px solid #e1e4e8; border-radius: 8px;
}
.content-area {
  resize: none; padding: 12px 14px; font-size: 15px;
  line-height: 1.6; min-height: 120px; border: 1px solid #e1e4e8; border-radius: 8px;
}

.editor-images { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.editor-img-wrap {
  width: 96px; height: 96px; border-radius: 8px; overflow: hidden; position: relative;
  background: var(--color-bg);
}
.editor-img-wrap img { width: 100%; height: 100%; object-fit: cover; }
.editor-img-status {
  position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5px;
  color: #fff; background: rgba(22, 26, 31, 0.48); font-size: 11px; font-weight: 600; backdrop-filter: blur(1px);
}
.editor-img-status.failed { background: rgba(97, 25, 18, 0.58); }
.editor-img-status button { width: 29px; height: 29px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: rgba(255,255,255,0.92); color: #c94c3e; }
.editor-img-wrap.is-done img { animation: image-uploaded 0.22s ease-out; }
.editor-img-rm {
  position: absolute; top: 4px; right: 4px; width: 22px; height: 22px;
  border: none; background: rgba(0,0,0,0.55); color: #fff; border-radius: 50%;
  font-size: 12px; cursor: pointer; display: flex; align-items: center; justify-content: center;
}
.editor-img-add {
  width: 96px; height: 96px; border: 2px dashed var(--color-line); border-radius: 8px;
  display: flex; align-items: center; justify-content: center; font-size: 32px;
  color: var(--color-text-muted); cursor: pointer; transition: all 0.2s;
}
.editor-img-add:hover { border-color: var(--color-primary); color: var(--color-primary); }
.editor-hint { font-size: 12px; color: var(--color-text-light); margin-top: 6px; }
@keyframes image-uploaded { from { opacity: 0.72; } to { opacity: 1; } }

.topic-section { padding-top: 8px; }
.topic-heading, .topic-subheading { display: flex; align-items: center; justify-content: space-between; }
.topic-heading { min-height: 28px; }
.topic-label { display: inline-flex; align-items: center; gap: 6px; margin: 0; color: var(--color-text); }
.topic-label svg { color: var(--color-primary); }
.topic-count { font-size: 12px; color: var(--color-text-light); font-variant-numeric: tabular-nums; }
.topic-input-row { position: relative; display: flex; align-items: center; margin-top: 10px; }
.topic-prefix { position: absolute; left: 13px; z-index: 1; color: var(--color-primary); font-size: 17px; font-weight: 800; pointer-events: none; }
.topic-input { min-width: 0; padding: 11px 48px 11px 31px; border: 1px solid #e1e4e8; border-radius: 8px; background: #f7f8f9; }
.topic-input:focus { background: #fff; border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(231, 121, 16, 0.1); }
.topic-add-btn {
  position: absolute; right: 7px; width: 34px; height: 34px; display: inline-flex; align-items: center; justify-content: center;
  border-radius: 50%; color: var(--color-primary); background: transparent; transition: background 0.18s, opacity 0.18s;
}
.topic-add-btn:hover:not(:disabled) { background: var(--color-primary-bg); }
.topic-add-btn:disabled { opacity: 0.35; cursor: default; }
.topic-discovery { min-height: 67px; margin-top: 10px; }
.topic-subheading { min-height: 25px; margin-bottom: 7px; color: #5f6368; font-size: 13px; font-weight: 700; }
.topic-loading-copy { display: inline-flex; align-items: center; gap: 4px; color: var(--color-text-light); font-size: 12px; font-weight: 400; }
.topic-refresh { display: inline-flex; align-items: center; gap: 4px; padding: 4px 6px; color: var(--color-primary); background: transparent; font-size: 12px; }
.topic-refresh:disabled { opacity: 0.5; }
.topic-suggestions { display: flex; flex-wrap: wrap; gap: 7px; max-height: 126px; overflow-y: auto; padding: 1px; }
.topic-sug-item {
  min-height: 30px; display: inline-flex; align-items: center; gap: 4px; padding: 4px 9px;
  border: 1px solid #e6e8eb; border-radius: 16px; background: #f7f7f8;
  font-size: 12px; cursor: pointer; color: #62676d; transition: background 0.18s, border-color 0.18s, color 0.18s;
}
.topic-sug-item svg { flex: none; color: var(--color-primary); }
.topic-sug-item small { color: #9aa0a6; font-size: 11px; font-variant-numeric: tabular-nums; }
.topic-sug-item:hover { background: var(--color-primary-bg); border-color: #ffd9b5; color: #c8660b; }
.topic-create-hint, .topic-retry { padding: 6px 1px; background: transparent; color: var(--color-text-muted); font-size: 13px; text-align: left; }
.topic-create-hint strong, .topic-retry { color: var(--color-primary); }
.topic-tags { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 8px; }
.topic-tag {
  display: inline-flex; align-items: center; gap: 4px;
  min-height: 30px; padding: 4px 8px 4px 10px; background: var(--color-primary-bg); color: var(--color-primary);
  border: 1px solid #ffd9b5; border-radius: 16px; font-size: 13px; font-weight: 600;
}
.topic-tag-rm { width: 20px; height: 20px; display: inline-flex; align-items: center; justify-content: center; border: none; border-radius: 50%; background: none; color: var(--color-primary); cursor: pointer; padding: 0; }
.topic-tag-rm:hover { background: rgba(231, 121, 16, 0.12); }
.topic-skeletons { display: flex; flex-wrap: wrap; gap: 7px; }
.topic-skeleton { width: 92px; height: 30px; border-radius: 16px; background: linear-gradient(90deg, #f0f1f2 25%, #fafafa 50%, #f0f1f2 75%); background-size: 200% 100%; animation: topic-shimmer 1.25s infinite; }
.topic-skeleton:nth-child(2), .topic-skeleton:nth-child(5) { width: 114px; }
.spin { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes topic-shimmer { to { background-position: -200% 0; } }

.vis-options { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
.vis-opt {
  display: flex; align-items: center; gap: 10px; padding: 12px 14px;
  border: 2px solid var(--color-line); border-radius: var(--radius-lg); cursor: pointer; transition: all 0.2s;
}
.vis-opt.active { border-color: var(--color-primary); background: var(--color-primary-bg); }
.vis-opt-icon { font-size: 20px; }
.vis-opt-copy { min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 2px; }
.vis-opt-label { font-weight: 700; font-size: 14px; color: var(--color-text); }
.vis-opt-desc { font-size: 12px; color: var(--color-text-muted); }

.editor-buttons { display: flex; gap: 10px; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--color-line); }
.btn-draft {
  flex: 1; padding: 12px; border: 1px solid var(--color-line); background: #fff;
  border-radius: var(--radius-lg); font-size: 15px; font-weight: 600; color: var(--color-text-muted);
  cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; justify-content: center; gap: 6px;
}
.btn-draft:hover { background: var(--color-bg); }
.btn-draft:disabled { opacity: 0.5; }
.btn-publish {
  flex: 2; padding: 12px; border: none; background: var(--color-primary); color: #fff;
  border-radius: var(--radius-lg); font-size: 15px; font-weight: 700;
  cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; justify-content: center; gap: 6px;
}
.btn-publish:hover { background: var(--color-primary-light); }
.btn-publish:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
