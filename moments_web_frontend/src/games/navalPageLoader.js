let page
export function loadNavalPage() {
  if (!page) page = import('../views/entertainment/NavalBattle.vue').catch(error => { page = null; throw error })
  return page
}
export function prefetchNavalPage() { loadNavalPage().catch(() => {}) }
