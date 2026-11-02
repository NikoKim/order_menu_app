<template>
  <div ref="root" class="custom-select" :class="{ disabled, open }">
    <button ref="trigger" type="button" class="custom-select-trigger" :disabled="disabled" @click="toggle">
      <span>{{ selected?.label || placeholder }}</span><ChevronDown :size="16" />
    </button>
    <Teleport :to="teleportTo">
      <Transition name="select-pop">
        <div v-if="open" ref="menu" class="custom-select-menu" :style="menuStyle">
          <button v-for="option in options" :key="String(option.value)" type="button"
            :class="{ selected: option.value === modelValue }" @click="choose(option.value)">
            <span>{{ option.label }}</span><Check v-if="option.value === modelValue" :size="15" />
          </button>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { Check, ChevronDown } from 'lucide-vue-next'

const props = defineProps({ modelValue: [String, Number, Boolean], options: { type: Array, default: () => [] }, placeholder: { type: String, default: '请选择' }, disabled: Boolean, teleportTo: { type: [String, Object], default: 'body' } })
const emit = defineEmits(['update:modelValue', 'change'])
const root = ref(null); const trigger = ref(null); const menu = ref(null); const open = ref(false); const menuStyle = ref({})
const selected = computed(() => props.options.find(item => item.value === props.modelValue))

function place() {
  if (!trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const below = window.innerHeight - rect.bottom, above = rect.top
  const desired = Math.min(280, props.options.length * 38 + 10)
  const upwards = below < desired + 10 && above > below
  const maxHeight = Math.min(desired, Math.max(60, (upwards ? above : below) - 12))
  const width = Math.min(Math.max(140, rect.width), window.innerWidth - 16)
  menuStyle.value = { position: 'fixed', left: `${Math.max(8, Math.min(rect.left, window.innerWidth - width - 8))}px`, top: `${upwards ? Math.max(8, rect.top - maxHeight - 5) : rect.bottom + 5}px`, width: `${width}px`, maxHeight: `${maxHeight}px` }
}
function close(event) { if (!event || (!root.value?.contains(event.target) && !menu.value?.contains(event.target))) { open.value = false; cleanup() } }
async function toggle() { if (props.disabled) return; open.value = !open.value; if (open.value) { await nextTick(); place(); document.addEventListener('pointerdown', close); window.addEventListener('resize', place); window.addEventListener('scroll', place, true) } else cleanup() }
function choose(value) { emit('update:modelValue', value); emit('change', value); open.value = false; cleanup() }
function cleanup() { document.removeEventListener('pointerdown', close); window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true) }
onBeforeUnmount(cleanup)
</script>

<style>
.custom-select{width:100%;min-width:0}.custom-select-trigger{width:100%;height:42px;display:flex;align-items:center;justify-content:space-between;gap:8px;padding:0 11px;border:1px solid var(--color-line,#d9dfdc);border-radius:6px;color:var(--color-text,#26342d);background:#fff;text-align:left}.custom-select-trigger span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.custom-select.open .custom-select-trigger{border-color:#4f896c;box-shadow:0 0 0 3px rgba(79,137,108,.12)}.custom-select.disabled{opacity:.55}.custom-select-menu{z-index:5000;overflow:auto;padding:4px;border:1px solid #d5ddd8;border-radius:7px;background:#fff;box-shadow:0 16px 45px rgba(25,38,31,.2)}.custom-select-menu button{width:100%;min-height:36px;display:flex;align-items:center;justify-content:space-between;gap:8px;padding:7px 9px;border-radius:4px;color:#34423a;background:transparent;text-align:left}.custom-select-menu button:hover,.custom-select-menu button.selected{color:#245f43;background:#edf6f1}.custom-select-menu button span{overflow-wrap:anywhere}.select-pop-enter-active,.select-pop-leave-active{transition:opacity .15s ease,transform .15s ease}.select-pop-enter-from,.select-pop-leave-to{opacity:0;transform:translateY(-4px)}
</style>
