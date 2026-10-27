import Lenis from 'lenis'
import 'lenis/dist/lenis.css'

/**
 * Inertia-based smooth scrolling for the whole site (mouse wheel and trackpad glide to a stop instead of
 * jumping). Skipped for visitors who ask their system for reduced motion.
 *
 * Wheel scrolling uses lerp (each frame closes a fixed share of the gap to the target), so every wheel tick
 * blends into the motion already under way. A duration/easing curve would restart on every tick and feel jerky.
 */
export function setupSmoothScroll(router) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

  const lenis = new Lenis({
    lerp: 0.075,
    wheelMultiplier: 0.9,
    touchMultiplier: 1.2,
    smoothWheel: true,
    autoRaf: true,
    // In-page links (#section) glide to their target instead of jumping.
    anchors: { duration: 1.2, easing: (t) => 1 - Math.pow(1 - t, 4) }, // ease-out-quart
  })

  // A new page starts at the top straight away rather than gliding up from the old position.
  router.afterEach(() => lenis.scrollTo(0, { immediate: true }))
}
