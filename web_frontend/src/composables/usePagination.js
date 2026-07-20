import { ref } from 'vue'

export function usePagination(apiFn, defaultParams = {}) {
  const records = ref([])
  const page = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const loading = ref(false)
  const error = ref(null)

  async function fetch(params = {}) {
    loading.value = true
    error.value = null
    try {
      const res = await apiFn({
        page: page.value,
        pageSize: pageSize.value,
        ...defaultParams,
        ...params
      })
      records.value = res.records || []
      page.value = res.page || 1
      total.value = res.total || 0
    } catch (e) {
      error.value = e.message
      records.value = []
    } finally {
      loading.value = false
    }
  }

  function setPage(p) {
    page.value = p
    return fetch({ page: p })
  }

  return { records, page, pageSize, total, loading, error, fetch, setPage }
}
