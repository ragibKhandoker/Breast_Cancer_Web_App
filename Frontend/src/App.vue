<script setup>
import { onMounted } from 'vue'
import AppHeader from './components/layout/AppHeader.vue'
import AppFooter from './components/layout/AppFooter.vue'
import AnnouncementBar from './components/layout/AnnouncementBar.vue'
import { loadSiteSettings } from './config/siteSettings'

onMounted(loadSiteSettings)
</script>

<template>
  <AnnouncementBar />
  <AppHeader />
  <main>
    <RouterView />
  </main>
  <AppFooter />
</template>
