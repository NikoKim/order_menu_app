<template>
  <div class="page-container log-page">
    <div class="page-header"><h1 class="page-title">开发日志</h1></div>
    <section v-for="(release, index) in releases" :key="release.version" class="release-card">
      <div class="release-heading">
        <span class="version">{{ release.version }}</span>
        <h2>{{ index === 0 ? '当前版本' : release.title }}</h2>
      </div>
      <ul>
        <li v-for="item in release.items" :key="item"><CircleCheck :size="15" /><span>{{ item }}</span></li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { CircleCheck } from 'lucide-vue-next'

const customerReleases = [
  { version: 'v2.1.1', items: ['重大更新：新增动态页', '优化动态页相关功能', '优化隐私相关功能', '优化聊天相关功能', '优化用户资料相关功能'] },
  { version: 'v1.3.1', title: '前版本', items: ['新增商家家庭群聊和私聊', '新增聊天及自动加入家庭群聊的隐私设置'] },
  { version: 'v1.2.1c', title: '前版本', items: ['优化订单、留言建议、反馈记录和家庭模块通知'] },
  { version: 'v1.2.1b', title: '前版本', items: ['优化页面布局和信息展示'] },
  { version: 'v1.2.1a', title: '前版本', items: ['优化全部商家和家庭商家展示', '优化首页、商家页和订单页加载动画', '家庭页增加家庭图像'] },
  { version: 'v1.2.1', title: '前版本', items: ['优化点餐页和首页布局', '加入版本检测能力'] },
  { version: 'v1.2', title: '前版本', items: ['点餐页调整为左侧分类、右侧菜品', '菜品规格支持可选、多选与数量限制', '不同规格在购物车中独立展示'] },
  { version: 'v1.1', title: '前版本', items: ['支持餐品规格和口味选项', '支持家庭编号、家庭图片和成员管理', '下单后自动扣减库存'] },
  { version: 'v1.0', title: '基础版本', items: ['浏览店铺、选择餐品、购物车结算和下单', '顾客、商家、管理员三端基础能力'] }
]
const shopReleases = [
  { version: 'v3.1.1', items: ['重大更新：新增动态页', '优化动态页相关功能', '优化隐私相关功能', '优化聊天相关功能', '优化用户资料相关功能'] },
  { version: 'v2.1.1', title: '前版本', items: ['新增商家家庭群聊和私聊，商家可管理家庭群聊', '新增隐私功能，可设置是否支持私聊'] },
  { version: 'v1.2.3b', title: '前版本', items: ['优化订单处理、家庭管理、顾客反馈模块消息通知'] },
  { version: 'v1.2.3a', title: '前版本', items: ['优化商家端页面布局和展示'] },
  { version: 'v1.2.3', title: '前版本', items: ['优化菜品类别删除功能', '新增首页展示设置功能'] },
  { version: 'v1.2.2', title: '前版本', items: ['支持调整分类顺序、名称和删除分类', '支持删除、上架和下架菜品'] },
  { version: 'v1.2', title: '前版本', items: ['菜品属性支持单选、多选及选择数量限制', '订单支持不接单并自动恢复库存', '底部入口展示待处理数量'] },
  { version: 'v1.1', title: '前版本', items: ['支持餐品规格和口味选项', '支持家庭成员管理及家庭订单标识', '下单后自动扣减库存'] },
  { version: 'v1.0', title: '基础版本', items: ['浏览店铺、选择餐品、购物车结算和下单', '商家端支持菜品、分类和订单基础管理'] }
]
const releases = JSON.parse(localStorage.getItem('user') || '{}')?.role === 'SHOP' ? shopReleases : customerReleases
</script>

<style scoped>
.log-page { max-width: 720px; }
.release-card { margin-bottom: 12px; padding: 16px; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius); }
.release-heading { display: flex; align-items: center; gap: 10px; }
.release-heading h2 { font-size: 18px; }
.version { padding: 4px 10px; border-radius: 999px; color: var(--color-primary); background: var(--color-primary-bg); font-size: 13px; font-weight: 800; }
ul { margin-top: 12px; list-style: none; }
li { display: flex; align-items: flex-start; gap: 8px; padding: 4px 0; color: var(--color-text-secondary); font-size: 14px; }
li svg { margin-top: 3px; flex: 0 0 auto; color: var(--color-primary); }
</style>
