import { createRouter, createWebHistory } from 'vue-router'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'navy', component: () => import('../views/game/NavalBattle.vue') },
    { path: '/login', component: () => import('../views/Account.vue') },
    { path: '/register', component: () => import('../views/Account.vue') },
    { path: '/admin', component: () => import('../views/Admin.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior: () => ({ top: 0 })
})
