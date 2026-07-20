import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')

function prepareImage(img) {
  img.classList.remove('app-image-loaded')
  img.classList.add('app-image-loading')
  const finish = () => {
    img.classList.remove('app-image-loading')
    img.classList.add('app-image-loaded')
  }
  if (img.complete) {
    requestAnimationFrame(finish)
    return
  }
  img.addEventListener('load', finish, { once: true })
  img.addEventListener('error', finish, { once: true })
}

const imageObserver = new MutationObserver(records => {
  records.forEach(record => {
    if (record.type === 'attributes' && record.target instanceof HTMLImageElement) {
      prepareImage(record.target)
      return
    }
    record.addedNodes.forEach(node => {
      if (!(node instanceof Element)) return
      if (node instanceof HTMLImageElement) prepareImage(node)
      node.querySelectorAll?.('img').forEach(prepareImage)
    })
  })
})

document.querySelectorAll('img').forEach(prepareImage)
imageObserver.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] })
