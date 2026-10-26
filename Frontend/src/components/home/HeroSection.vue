<script setup>
import AppIcon from '../common/AppIcon.vue'
import PinkRibbon from '../common/PinkRibbon.vue'
import MammogramMonitor from '../common/MammogramMonitor.vue'
import LeafSpray from '../common/LeafSpray.vue'
import YouTubeEmbed from '../common/YouTubeEmbed.vue'
import { HERO_VIDEO_URL } from '../../config/siteConfig'
import { APP_ROUTES } from '../../config/appRoutes'
</script>

<template>
  <section class="hero">
    <div class="container hero-inner">
      <div v-reveal class="copy">
        <h1>
          <span class="line-dark">Early Detection</span>
          <span class="line-pink">Saves Lives</span>
        </h1>
        <p>Upload a mammography image or learn more about breast cancer. Together, we can fight it!</p>
        <div class="cta">
          <RouterLink :to="APP_ROUTES.analysis" class="btn btn-primary">Start Analysis <AppIcon name="arrow-right" :size="18" /></RouterLink>
          <RouterLink to="/how-it-works" class="btn btn-outline">How It Works</RouterLink>
        </div>
        <p class="account">
          <RouterLink :to="APP_ROUTES.login">Login</RouterLink> or
          <RouterLink :to="APP_ROUTES.register">create an account</RouterLink>
        </p>
      </div>

      <div v-reveal="200" class="art">
        <LeafSpray class="leaf leaf-left" aria-hidden="true" />
        <LeafSpray class="leaf leaf-right" aria-hidden="true" />
        <YouTubeEmbed v-if="HERO_VIDEO_URL" :url="HERO_VIDEO_URL" title="Breast cancer awareness video" class="video-frame" />
        <MammogramMonitor v-else class="monitor" aria-hidden="true" />
        <PinkRibbon class="ribbon" :size="110" aria-hidden="true" />
        <AppIcon name="heart" class="float-heart h1" :size="26" />
        <AppIcon name="heart" class="float-heart h2" :size="20" />
        <AppIcon v-if="!HERO_VIDEO_URL" name="heart" class="float-heart h3" :size="16" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero {
  /* Transparent so the Home page background photo shows through clearly. */
  background: transparent;
  padding: 3.5rem 0 6rem;
  overflow: hidden;
}
.hero-inner { display: grid; grid-template-columns: 1.05fr 1fr; align-items: center; gap: 2rem; }

h1 { margin: 0; font-size: clamp(2.4rem, 5vw, 3.8rem); font-weight: 800; line-height: 1.08; letter-spacing: -0.5px; }
.line-dark { display: block; color: var(--purple-900); }
.line-pink { display: block; color: var(--pink-600); }
.copy p { font-size: 1.2rem; color: var(--text); max-width: 440px; margin: 1.25rem 0 2rem; line-height: 1.5; }
.cta { display: flex; flex-wrap: wrap; gap: 1rem; }
.copy .account { margin-top: 1.25rem; margin-bottom: 0; font-size: 0.95rem; }
.account a { color: var(--pink-600); font-weight: 600; }
.account a:hover { text-decoration: underline; }

.art { position: relative; height: 360px; }
.monitor { position: absolute; right: 6%; bottom: 0; width: 70%; filter: drop-shadow(0 18px 30px rgba(42, 22, 80, 0.25)); }
.video-frame {
  position: absolute; right: 4%; top: 50%; transform: translateY(-50%); width: 84%;
  border: 8px solid #2e2647; box-shadow: 0 18px 40px rgba(42, 22, 80, 0.3);
}
.ribbon { position: absolute; left: -2%; top: -4%; z-index: 1; transform: rotate(-12deg); filter: drop-shadow(0 10px 18px rgba(216, 27, 106, 0.3)); }
.ribbon, .leaf, .float-heart { pointer-events: none; }
.leaf { position: absolute; bottom: 0; width: 70px; }
.leaf-left { left: 2%; }
.leaf-right { right: -2%; transform: scaleX(-1); }
.float-heart { position: absolute; color: var(--pink-500); fill: #f7a8c8; }
.h1 { left: 34%; top: 6%; }
.h2 { right: 8%; top: 4%; }
.h3 { left: 26%; top: 62%; }

@media (max-width: 860px) {
  .hero { padding: 2.5rem 0 5rem; }
  .hero-inner { grid-template-columns: 1fr; text-align: center; }
  .copy p { margin-left: auto; margin-right: auto; }
  .cta { justify-content: center; }
  /* Single column: let the video or monitor size the box instead of a fixed height, so it never spills
     onto the highlight bar below. */
  .art { height: auto; padding: 1.5rem 6% 1rem; }
  .video-frame, .monitor { position: relative; top: auto; right: auto; transform: none; margin: 0 auto; }
  .video-frame { width: 100%; max-width: 640px; }
  .monitor { width: 80%; }
  .ribbon { width: 64px; height: auto; left: 0; top: 0; }
}
</style>
