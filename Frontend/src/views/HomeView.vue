<script setup>
import HeroSection from '../components/home/HeroSection.vue'
import HighlightBar from '../components/home/HighlightBar.vue'
import AboutSection from '../components/home/AboutSection.vue'
import FeatureGrid from '../components/home/FeatureGrid.vue'
import HowItWorksPreview from '../components/home/HowItWorksPreview.vue'
import FaqPreview from '../components/home/FaqPreview.vue'
import CommunityBanner from '../components/home/CommunityBanner.vue'
</script>

<template>
  <div class="photo-bg">
    <HeroSection />
    <HighlightBar />
    <AboutSection />
    <FeatureGrid />
    <HowItWorksPreview />
    <FaqPreview />
    <CommunityBanner />
  </div>
</template>
