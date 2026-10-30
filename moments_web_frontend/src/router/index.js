import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/customer',
    component: () => import('../layouts/MomentsLayout.vue'),
    children: [
      { path: '', redirect: '/customer/moments' },
      { path: 'login', name: 'customer-login', component: () => import('../views/customer/CustomerLogin.vue'), meta: { guest: true, bare: true } },
      { path: 'register', name: 'customer-register', component: () => import('../views/customer/CustomerRegister.vue'), meta: { guest: true, bare: true } },
      { path: 'chat', name: 'customer-chat', component: () => import('../views/customer/ChatHome.vue'), meta: { auth: 'CUSTOMER', shell: true } },
      { path: 'moments', name: 'customer-moments', component: () => import('../views/customer/ChatHome.vue'), meta: { shell: true } },
      { path: 'entertainment', name: 'customer-entertainment', component: () => import('../views/entertainment/EntertainmentHub.vue'), meta: { entertainment: true, wide: true } },
      { path: 'entertainment/tutorial', name: 'customer-entertainment-tutorial', component: () => import('../views/entertainment/GuessStockPlay.vue'), meta: { tutorial: true, wide: true, immersive: true } },
      { path: 'entertainment/games/:gameId', name: 'customer-entertainment-game', component: () => import('../views/entertainment/GameDetail.vue'), meta: { entertainment: true, wide: true } },
      { path: 'entertainment/sessions/:sessionId/play', name: 'customer-entertainment-play', component: () => import('../views/entertainment/GuessStockPlay.vue'), meta: { entertainment: true, wide: true, immersive: true } },
      { path: 'entertainment/challenges/:challengeId/leaderboard', name: 'customer-entertainment-challenge-leaderboard', component: () => import('../views/entertainment/DailyChallengeLeaderboard.vue'), meta: { entertainment: true, wide: true } },
      { path: 'entertainment/rooms/:roomId', name: 'customer-entertainment-room', component: () => import('../views/entertainment/RoomLobby.vue'), meta: { entertainment: true, wide: true } },
      { path: 'entertainment/history', name: 'customer-entertainment-history', component: () => import('../views/entertainment/EntertainmentHistory.vue'), meta: { auth: 'CUSTOMER', entertainment: true, wide: true } },
      { path: 'entertainment/create', name: 'customer-entertainment-create', component: () => import('../views/entertainment/GameEditor.vue'), meta: { auth: 'CUSTOMER', entertainment: true, wide: true } },
      { path: 'entertainment/games/:id/edit', name: 'customer-entertainment-edit', component: () => import('../views/entertainment/GameEditor.vue'), meta: { auth: 'CUSTOMER', entertainment: true, wide: true } },
      { path: 'entertainment/my-games', name: 'customer-entertainment-my-games', component: () => import('../views/entertainment/MyGames.vue'), meta: { auth: 'CUSTOMER', entertainment: true, wide: true } },
      { path: 'chat/:id', name: 'customer-chat-detail', component: () => import('../views/chat/ChatDetail.vue'), meta: { auth: 'CUSTOMER', immersive: true } },
      { path: 'chat/:id/essences', name: 'customer-chat-essences', component: () => import('../views/chat/ChatEssence.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'chat/:id/group-info', name: 'customer-family-group-info', component: () => import('../views/chat/FamilyGroupInfo.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'chat/user/:userId/moments', name: 'customer-user-moments', component: () => import('../views/moments/UserMoments.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'chat/user/:userId', name: 'customer-chat-user', component: () => import('../views/chat/ChatUserProfile.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'privacy', name: 'customer-chat-settings', component: () => import('../views/chat/ChatSettings.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'moment-privacy', name: 'customer-privacy', component: () => import('../views/customer/PrivacySettings.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'message-center', name: 'customer-message-center', component: () => import('../views/customer/NotificationsPage.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'notifications', redirect: '/customer/message-center' },
      { path: 'my-moments', name: 'customer-my-moments', component: () => import('../views/customer/MyMoments.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'moments/new', name: 'customer-moment-new', component: () => import('../views/moments/MomentEditor.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'moments/search', name: 'customer-moment-search', component: () => import('../views/moments/MomentSearch.vue') },
      { path: 'moments/drafts', name: 'customer-moment-drafts', component: () => import('../views/moments/MomentDrafts.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'moments/topics/:id', name: 'customer-moment-topic', component: () => import('../views/moments/MomentTopic.vue') },
      { path: 'moments/:id', name: 'customer-moment-detail', component: () => import('../views/moments/MomentDetail.vue') },
      { path: 'shop/:id/menu', name: 'shop-menu', component: () => import('../views/customer/ShopMenu.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'shops/:shopId/dishes/:dishId', name: 'customer-dish-detail', component: () => import('../views/customer/DishDetail.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'orders/:id', name: 'customer-order-detail', component: () => import('../views/customer/OrderDetail.vue'), meta: { auth: 'CUSTOMER' } }
    ]
  },
  { path: '/', redirect: '/customer/moments' },
  { path: '/:pathMatch(.*)*', redirect: '/customer/moments' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() { return { top: 0 } }
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  let role = null
  try { role = JSON.parse(localStorage.getItem('user') || 'null')?.role } catch { }

  if (to.meta.auth && (!token || role !== 'CUSTOMER')) {
    return { path: '/customer/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guest && token && role === 'CUSTOMER') return '/customer/chat'
})

export default router
