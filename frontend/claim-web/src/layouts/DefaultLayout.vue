<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const menus = [
  { path: '/', title: '平台首页' },
  { path: '/application', title: '申请管理' },
  { path: '/case', title: '案件管理' },
  { path: '/diagnosis', title: 'AI 异常诊断' },
  { path: '/tenant', title: '租户管理' }
]
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px">
      <div class="brand">理赔业务集成平台</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path">
          {{ menu.title }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.brand {
  height: 56px;
  line-height: 56px;
  padding: 0 16px;
  font-weight: 600;
}

.el-menu {
  border-right: none;
}
</style>
