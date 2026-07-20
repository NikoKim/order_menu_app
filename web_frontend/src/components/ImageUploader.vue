<template>
  <div class="image-uploader">
    <div v-if="preview" class="image-preview">
      <img :src="preview" alt="preview" />
      <button class="image-remove" @click="removeImage">&times;</button>
    </div>
    <label v-else class="image-upload-btn">
      <input type="file" accept="image/*" @change="handleFile" hidden />
      <span class="upload-icon">+</span>
      <span class="upload-text">{{ placeholder }}</span>
    </label>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '上传图片' }
})
const emit = defineEmits(['update:modelValue'])

const preview = ref(props.modelValue || '')
watch(() => props.modelValue, v => { preview.value = v || '' })

async function handleFile(e) {
  const file = e.target.files[0]
  if (!file) return
  preview.value = URL.createObjectURL(file)
  emit('update:modelValue', preview.value)
}

function removeImage() {
  preview.value = ''
  emit('update:modelValue', '')
}
</script>

<style scoped>
.image-uploader { display: inline-block; vertical-align: top; }
.image-upload-btn {
  width: 100px; height: 100px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  border: 2px dashed var(--color-line); border-radius: var(--radius);
  cursor: pointer; color: var(--color-text-muted); gap: 4px;
  transition: border-color 0.2s;
}
.image-upload-btn:hover { border-color: var(--color-primary); }
.upload-icon { font-size: 28px; line-height: 1; }
.upload-text { font-size: 12px; }
.image-preview {
  position: relative; width: 100px; height: 100px; border-radius: var(--radius); overflow: hidden;
}
.image-preview img { width: 100%; height: 100%; object-fit: cover; }
.image-remove {
  position: absolute; top: 2px; right: 2px;
  width: 22px; height: 22px; border-radius: 50%;
  background: rgba(0,0,0,0.5); color: #fff;
  font-size: 14px; line-height: 1;
  display: flex; align-items: center; justify-content: center;
}
</style>
