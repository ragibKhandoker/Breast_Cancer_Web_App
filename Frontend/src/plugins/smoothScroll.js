import Lenis from 'lenis'
import 'lenis/dist/lenis.css'

/**
 * Inertia-based smooth scrolling for the whole site (mouse wheel and trackpad glide to a stop instead of
 * jumping). Skipped for visitors who ask their system for reduced motion.
 */
export function setupSmoothScroll(router) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

  const lenis = new Lenis({
    duration: 1.1,
    easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)), // ease-out-expo
    smoothWheel: true,
  })

  function raf(time) {
    lenis.raf(time)
    requestAnimationFrame(raf)
  }
  requestAnimationFrame(raf)

  // A new page starts at the top straight away rather than gliding up from the old position.
  router.afterEach(() => lenis.scrollTo(0, { immediate: true }))
}
