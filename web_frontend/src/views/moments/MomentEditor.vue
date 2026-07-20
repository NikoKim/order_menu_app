<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">{{ editId ? '编辑动态' : '发布动态' }}</h1>
    </div>

    <div class="editor-card">
      <div class="form-group">
        <input v-model="form.title" class="form-input title-input" maxlength="120" placeholder="添加标题（可选）" />
      </div>
      <div class="form-group">
        <textarea v-model="form.content" class="form-textarea content-area" rows="6" maxlength="20000" placeholder="分享你的想法..."></textarea>
      </div>

      <!-- Images -->
      <div class="form-group">
        <div class="editor-images">
          <div v-for="(img, i) in form.imageUrls" :key="i" class="editor-img-wrap">
            <img :src="img.startsWith('blob:') ? img : imageUrl(img)" alt="" />
            <button class="editor-img-rm" title="移除图片" @click="form.imageUrls.splice(i, 1)"><X :size="15" /></button>
          </div>
          <label v-if="form.imageUrls.length < 9" class="editor-img-add">
            <ImagePlus :size="25" />
            <input type="file" accept="image/*" hidden @change="handleImageUpload" />
          </label>
        </div>
        <p class="editor-hint">最多9张图片</p>
      </div>

      <!-- Topics -->
      <div class="form-group">
        <div class="topic-input-row">
          <input v-model="topicInput" class="form-input" placeholder="添加话题，回车确认" @keyup.enter.prevent="addTopic" @input="searchTopics" />
        </div>
        <div v-if="topicSuggestions.length" class="topic-suggestions">
          <button v-for="t in topicSuggestions" :key="t.id" class="topic-sug-item" @click="selectTopic(t)">#{{ t.name }}</button>
        </div>
        <div v-if="form.topics.length" class="topic-tags">
          <span v-for="(t, i) in form.topics" :key="i" class="topic-tag">
            #{{ t }} <button class="topic-tag-rm" title="移除话题" @click="form.topics.splice(i, 1)"><X :size="13" /></button>
          </span>
        </div>
      </div>

      <!-- Visibility -->
      <div class="form-group">
        <label class="form-label">谁可以看到</label>
        <div class="vis-options">
          <label v-for="opt in visOpts" :key="opt.value" class="vis-opt" :class="{ active: form.visibility === opt.value }">
            <input type="radio" v-model="form.visibility" :value="opt.value" hidden />
            <component :is="opt.icon" class="vis-opt-icon" :size="20" />
            <span class="vis-opt-label">{{ opt.label }}</span>
            <span class="vis-opt-desc">{{ opt.desc }}</span>
          </label>
        </div>
      </div>

      <p v-if="error" class="form-error">{{ error }}</p>
      <div class="editor-buttons">
        <button class="btn-draft" @click="saveDraft" :disabled="saving"><Save :size="18" />存草稿</button>
        <button class="btn-publish" @click="publish" :disabled="saving"><Send :size="18" />{{ saving ? '发布中...' : '发布动态' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Globe2, ImagePlus, LockKeyhole, Save, Send, UsersRound, X } from 'lucide-vue-next'
import { createMoment, updateMoment, getMomentDetail, uploadMomentImage, getTopicSuggestions, createTopic } from '../../api/moments'
import { imageUrl } from '../../composables/useImage'

const route = useRoute()
const router = useRouter()
const editId = ref(route.query.editId ? Number(route.query.editId) : null)

const form = ref({
  title: '', content: '', visibility: 'PUBLIC', topics: [], imageUrls: []
})

const visOpts = [
  { value: 'PUBLIC', label: '公开', desc: '所有人可见', icon: Globe2 },
  { value: 'RELATED', label: '相关', desc: '好友和家人可见', icon: UsersRound },
  { value: 'PRIVATE', label: '私密', desc: '仅自己可见', icon: LockKeyhole }
]

const topicInput = ref('')
const topicSuggestions = ref([])
const saving = ref(false)
const error = ref('')

async function handleImageUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  error.value = ''
  try {
    const preview = URL.createObjectURL(file)
    form.value.imageUrls.push(preview)
    const res = await uploadMomentImage(file)
    const idx = form.value.imageUrls.indexOf(preview)
    if (idx >= 0) form.value.imageUrls[idx] = res.url
  } catch (err) {
    error.value = err.message
    form.value.imageUrls = form.value.imageUrls.filter(u => !u.startsWith('blob:'))
  }
  e.target.value = ''
}

async function searchTopics() {
  const q = topicInput.value.trim()
  if (!q) { topicSuggestions.value = []; return }
  try { topicSuggestions.value = await getTopicSuggestions(q) } catch { topicSuggestions.value = [] }
}

function addTopic() {
  const name = topicInput.value.trim().replace(/^#/, '')
  if (!name || form.value.topics.length >= 5 || form.value.topics.includes(name)) return
  form.value.topics.push(name)
  topicInput.value = ''
  topicSuggestions.value = []
}

function selectTopic(t) {
  if (form.value.topics.length >= 5 || form.value.topics.includes(t.name)) return
  form.value.topics.push(t.name)
  topicInput.value = ''
  topicSuggestions.value = []
}

async function publish() {
  error.value = ''
  saving.value = true
  try {
    const data = {
      title: form.value.title.trim(), content: form.value.content.trim(),
      visibility: form.value.visibility, status: 'PUBLISHED',
      topics: form.value.topics,
      imageUrls: form.value.imageUrls.filter(u => !u.startsWith('blob:'))
    }
    if (editId.value) await updateMoment(editId.value, data)
    else await createMoment(data)
    router.back()
  } catch (e) { error.value = e.message }
  finally { saving.value = false }
}

async function saveDraft() {
  error.value = ''
  saving.value = true
  try {
    const data = {
      title: form.value.title.trim(), content: form.value.content.trim(),
      visibility: form.value.visibility, status: 'DRAFT',
      topics: form.value.topics,
      imageUrls: form.value.imageUrls.filter(u => !u.startsWith('blob:'))
    }
    if (editId.value) await updateMoment(editId.value, data)
    else await createMoment(data)
    router.back()
  } catch (e) { error.value = e.message }
  finally { saving.value = false }
}

onMounted(async () => {
  if (editId.value) {
    try {
      const m = await getMomentDetail(editId.value)
      form.value = {
        title: m.title || '', content: m.content || '',
        visibility: m.visibility || 'PUBLIC',
        topics: (m.topics || []).map(t => t.name),
        imageUrls: m.imageUrls || []
      }
    } catch { }
  }
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

.title-input {
  font-size: 18px; font-weight: 700; border: none; padding: 12px 0;
  border-bottom: 1px solid var(--color-line); border-radius: 0;
}
.title-input:focus { border-color: var(--color-primary); }
.content-area {
  border: none; resize: none; padding: 12px 0; font-size: 15px;
  line-height: 1.6; min-height: 120px; border-radius: 0;
}

.editor-images { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.editor-img-wrap {
  width: 96px; height: 96px; border-radius: 8px; overflow: hidden; position: relative;
  background: var(--color-bg);
}
.editor-img-wrap img { width: 100%; height: 100%; object-fit: cover; }
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

.topic-input-row { display: flex; gap: 8px; margin-top: 16px; }
.topic-suggestions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; max-height: 120px; overflow-y: auto; }
.topic-sug-item {
  padding: 4px 12px; border: 1px solid var(--color-line); border-radius: 20px;
  background: none; font-size: 13px; cursor: pointer; color: var(--color-primary);
}
.topic-sug-item:hover { background: var(--color-primary-bg); }
.topic-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.topic-tag {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 10px; background: var(--color-primary-bg); color: var(--color-primary);
  border-radius: 20px; font-size: 13px; font-weight: 600;
}
.topic-tag-rm { border: none; background: none; color: var(--color-primary); cursor: pointer; font-size: 14px; padding: 0; line-height: 1; }

.vis-options { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
.vis-opt {
  display: flex; align-items: center; gap: 10px; padding: 12px 14px;
  border: 2px solid var(--color-line); border-radius: var(--radius-lg); cursor: pointer; transition: all 0.2s;
}
.vis-opt.active { border-color: var(--color-primary); background: var(--color-primary-bg); }
.vis-opt-icon { font-size: 20px; }
.vis-opt-label { font-weight: 700; font-size: 14px; color: var(--color-text); }
.vis-opt-desc { font-size: 12px; color: var(--color-text-muted); }

.editor-buttons { display: flex; gap: 10px; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--color-line); }
.btn-draft {
  flex: 1; padding: 12px; border: 1px solid var(--color-line); background: #fff;
  border-radius: var(--radius-lg); font-size: 15px; font-weight: 600; color: var(--color-text-muted);
  cursor: pointer; transition: all 0.2s;
}
.btn-draft:hover { background: var(--color-bg); }
.btn-draft:disabled { opacity: 0.5; }
.btn-publish {
  flex: 2; padding: 12px; border: none; background: var(--color-primary); color: #fff;
  border-radius: var(--radius-lg); font-size: 15px; font-weight: 700;
  cursor: pointer; transition: all 0.2s;
}
.btn-publish:hover { background: var(--color-primary-light); }
.btn-publish:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
