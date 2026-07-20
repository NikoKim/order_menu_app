import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // ===== Shop Routes =====
  {
    path: '/shop',
    component: () => import('../layouts/ShopLayout.vue'),
    children: [
      { path: '', redirect: '/shop/login' },
      { path: 'login', name: 'shop-login', component: () => import('../views/shop/ShopLogin.vue'), meta: { guest: true } },
      { path: 'dashboard', name: 'shop-dashboard', component: () => import('../views/shop/ShopDashboard.vue'), meta: { auth: 'SHOP' } },
      { path: 'dishes', name: 'shop-dishes', component: () => import('../views/shop/DishManage.vue'), meta: { auth: 'SHOP' } },
      { path: 'dishes/new', name: 'shop-dish-new', component: () => import('../views/shop/DishEdit.vue'), meta: { auth: 'SHOP' } },
      { path: 'dishes/:id/edit', name: 'shop-dish-edit', component: () => import('../views/shop/DishEdit.vue'), meta: { auth: 'SHOP' } },
      { path: 'orders', name: 'shop-orders', component: () => import('../views/shop/OrderManage.vue'), meta: { auth: 'SHOP' } },
      { path: 'orders/:id', name: 'shop-order-detail', component: () => import('../views/shop/OrderDetail.vue'), meta: { auth: 'SHOP' } },
      { path: 'feedback', name: 'shop-feedback', component: () => import('../views/shop/FeedbackManage.vue'), meta: { auth: 'SHOP' } },
      { path: 'profile', name: 'shop-profile', component: () => import('../views/shop/ShopProfile.vue'), meta: { auth: 'SHOP' } },
      { path: 'notifications', name: 'shop-notifications', component: () => import('../views/customer/NotificationsPage.vue'), meta: { auth: 'SHOP' } },
      { path: 'message-center', name: 'shop-message-center', component: () => import('../views/customer/NotificationsPage.vue'), meta: { auth: 'SHOP' } },
      { path: 'privacy', name: 'shop-chat-settings', component: () => import('../views/chat/ChatSettings.vue'), meta: { auth: 'SHOP' } },
      { path: 'development-log', name: 'shop-development-log', component: () => import('../views/shared/DevelopmentLog.vue'), meta: { auth: 'SHOP' } },
      { path: 'showcase', name: 'shop-showcase', component: () => import('../views/shop/ShowcaseManage.vue'), meta: { auth: 'SHOP' } },
      { path: 'family', name: 'shop-family', component: () => import('../views/shop/ShopFamily.vue'), meta: { auth: 'SHOP' } },
      { path: 'chat', name: 'shop-chat', component: () => import('../views/shop/ShopChat.vue'), meta: { auth: 'SHOP' } },
      { path: 'chat/:id', name: 'shop-chat-detail', component: () => import('../views/chat/ChatDetail.vue'), meta: { auth: 'SHOP' } },
      { path: 'chat/:id/essences', name: 'shop-chat-essences', component: () => import('../views/chat/ChatEssence.vue'), meta: { auth: 'SHOP' } },
      { path: 'chat/:id/group-info', name: 'shop-family-group-info', component: () => import('../views/chat/FamilyGroupInfo.vue'), meta: { auth: 'SHOP' } },
      { path: 'chat/:id/group-manage', name: 'shop-family-group-manage', component: () => import('../views/shop/FamilyGroupManage.vue'), meta: { auth: 'SHOP' } },
      { path: 'chat/user/:userId', name: 'shop-chat-user', component: () => import('../views/chat/ChatUserProfile.vue'), meta: { auth: 'SHOP' } },
      { path: 'moments/new', name: 'shop-moment-new', component: () => import('../views/moments/MomentEditor.vue'), meta: { auth: 'SHOP' } },
      { path: 'moments/:id', name: 'shop-moment-detail', component: () => import('../views/moments/MomentDetail.vue'), meta: { auth: 'SHOP' } }
    ]
  },
  // ===== Customer Routes =====
  {
    path: '/customer',
    component: () => import('../layouts/CustomerLayout.vue'),
    children: [
      { path: '', redirect: '/customer/home' },
      { path: 'home', name: 'customer-home', component: () => import('../views/customer/HomePage.vue') },
      { path: 'login', name: 'customer-login', component: () => import('../views/customer/CustomerLogin.vue'), meta: { guest: true } },
      { path: 'register', name: 'customer-register', component: () => import('../views/customer/CustomerRegister.vue'), meta: { guest: true } },
      { path: 'shop/:id/menu', name: 'shop-menu', component: () => import('../views/customer/ShopMenu.vue'), props: true },
      { path: 'shops/:shopId/dishes/:dishId', name: 'customer-dish-detail', component: () => import('../views/customer/DishDetail.vue') },
      { path: 'dishes/:id/share', name: 'customer-dish-share', component: () => import('../views/customer/ShareTargetPage.vue'), meta: { auth: 'CUSTOMER', shareKind: 'dish' } },
      { path: 'shops/:id/share', name: 'customer-shop-share', component: () => import('../views/customer/ShareTargetPage.vue'), meta: { auth: 'CUSTOMER', shareKind: 'shop' } },
      { path: 'checkout', name: 'checkout', component: () => import('../views/customer/Checkout.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'orders', name: 'customer-orders', component: () => import('../views/customer/OrderList.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'orders/:id', name: 'customer-order-detail', component: () => import('../views/customer/OrderDetail.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'orders/:id/share', name: 'customer-order-share', component: () => import('../views/customer/ShareTargetPage.vue'), meta: { auth: 'CUSTOMER', shareKind: 'order' } },
      { path: 'feedback', name: 'customer-feedback', component: () => import('../views/customer/FeedbackPage.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'feedback/history', name: 'customer-my-feedback', component: () => import('../views/customer/MyFeedback.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'families', name: 'customer-families', component: () => import('../views/customer/MyFamilies.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'profile', name: 'customer-profile', component: () => import('../views/customer/Profile.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'profile/edit', name: 'customer-profile-edit', component: () => import('../views/customer/ProfileEdit.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'notifications', name: 'customer-notifications', component: () => import('../views/customer/NotificationsPage.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'message-center', name: 'customer-message-center', component: () => import('../views/customer/NotificationsPage.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'privacy', name: 'customer-chat-settings', component: () => import('../views/chat/ChatSettings.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'moment-privacy', name: 'customer-privacy', component: () => import('../views/customer/PrivacySettings.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'development-log', name: 'customer-development-log', component: () => import('../views/shared/DevelopmentLog.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'my-moments', name: 'customer-my-moments', component: () => import('../views/customer/MyMoments.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'chat', name: 'customer-chat', component: () => import('../views/customer/ChatHome.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'chat/:id', name: 'customer-chat-detail', component: () => import('../views/chat/ChatDetail.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'chat/:id/essences', name: 'customer-chat-essences', component: () => import('../views/chat/ChatEssence.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'chat/:id/group-info', name: 'customer-family-group-info', component: () => import('../views/chat/FamilyGroupInfo.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'chat/user/:userId', name: 'customer-chat-user', component: () => import('../views/chat/ChatUserProfile.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'moments/new', name: 'customer-moment-new', component: () => import('../views/moments/MomentEditor.vue'), meta: { auth: 'CUSTOMER' } },
      { path: 'moments/:id', name: 'customer-moment-detail', component: () => import('../views/moments/MomentDetail.vue'), meta: { auth: 'CUSTOMER' } }
    ]
  },
  // ===== Admin Routes =====
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/admin/login' },
      { path: 'login', name: 'admin-login', component: () => import('../views/admin/AdminLogin.vue'), meta: { guest: true } },
      { path: 'users', name: 'admin-users', component: () => import('../views/admin/UserManage.vue'), meta: { auth: 'ADMIN' } }
    ]
  },
  // ===== Root Redirect =====
  { path: '/', redirect: '/customer/home' },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFound.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

// Auth guard
const ROLE_PATH = { SHOP: '/shop', CUSTOMER: '/customer', ADMIN: '/admin' }

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  let userRole = null
  try {
    const user = JSON.parse(localStorage.getItem('user') || 'null')
    userRole = user?.role
  } catch { }

  const requiresAuth = to.meta.auth
  const isGuestRoute = to.meta.guest

  if (requiresAuth && !token) {
    const loginPath = ROLE_PATH[requiresAuth] ? `${ROLE_PATH[requiresAuth]}/login` : '/customer/login'
    return next(loginPath)
  }

  // Only redirect guest routes within the same portal
  if (isGuestRoute && token && userRole) {
    const guestInPortal = to.path.startsWith(ROLE_PATH[userRole])
    if (guestInPortal) {
      return next(ROLE_PATH[userRole])
    }
  }

  next()
})

export default router
