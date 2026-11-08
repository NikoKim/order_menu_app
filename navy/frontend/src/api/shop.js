import client from './client'

export const getShop = () => client.get('/naval/shop')
export const purchaseShopItem = (itemId, requestId) => client.post('/naval/shop/purchases', { itemId, requestId })
export const useInventoryItem = (id, requestId) => client.post(`/naval/shop/inventory/${encodeURIComponent(id)}/use`, { requestId })
export const equipInventoryItem = (inventoryId, requestId) => client.post('/naval/shop/equipment', { inventoryId, requestId })
