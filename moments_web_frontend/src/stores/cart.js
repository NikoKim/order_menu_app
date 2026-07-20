import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useCartStore = defineStore('cart', () => {
  const items = ref([])
  const shopId = ref(null)

  const count = computed(() => items.value.reduce((s, i) => s + i.quantity, 0))
  const totalAmount = computed(() =>
    items.value.reduce((sum, item) => {
      const optionAmt = item.selectedOptions?.reduce((s, o) => s + (o.priceDelta || 0), 0) || 0
      return sum + (item.price + optionAmt) * item.quantity
    }, 0)
  )

  function initShop(id) {
    if (shopId.value !== id) {
      items.value = []
      shopId.value = id
    }
  }

  function addItem(dish, quantity = 1, selectedOptions = []) {
    const key = dish.id + '_' + selectedOptions.map(o => o.id).sort().join(',')
    const existing = items.value.find(i => i._key === key)
    if (existing) {
      existing.quantity += quantity
    } else {
      items.value.push({
        _key: key,
        dishId: dish.id,
        name: dish.name,
        price: dish.price,
        imageUrl: dish.imageUrl,
        quantity,
        selectedOptions,
        optionItemIds: selectedOptions.map(o => o.id)
      })
    }
  }

  function removeItem(key) {
    items.value = items.value.filter(i => i._key !== key)
  }

  function updateQuantity(key, qty) {
    const item = items.value.find(i => i._key === key)
    if (item) {
      if (qty <= 0) removeItem(key)
      else item.quantity = qty
    }
  }

  function clearCart() {
    items.value = []
    shopId.value = null
  }

  return { items, shopId, count, totalAmount, initShop, addItem, removeItem, updateQuantity, clearCart }
})
