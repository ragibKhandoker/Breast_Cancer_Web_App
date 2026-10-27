/**
 * v-reveal: fades and slides an element in whenever it scrolls into view, in both directions. Scrolling down,
 * it rises from below; scrolling back up, it drops in from above. Once it is fully off screen it resets, so
 * the effect plays again next time.
 * Optional value is a delay in milliseconds, used to stagger items in a row: v-reveal="150".
 * The look is defined by the .reveal / .reveal--above / .is-visible classes in assets/main.css.
 */
const observer =
  typeof IntersectionObserver !== 'undefined'
    ? new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            const el = entry.target
            if (entry.intersectionRatio >= 0.12) {
              if (el.classList.contains('is-visible')) continue
              el.style.transitionDelay = el.dataset.revealDelay || ''
              el.classList.add('is-visible')
              // Drop the stagger delay once revealed, so later hover effects respond immediately.
              el.addEventListener('transitionend', () => (el.style.transitionDelay = ''), { once: true })
            } else if (entry.intersectionRatio === 0) {
              // Fully gone: reset, remembering which edge it left by so it comes back in from that side.
              el.style.transitionDelay = ''
              el.classList.remove('is-visible')
              el.classList.toggle('reveal--above', entry.boundingClientRect.top < (entry.rootBounds?.top ?? 0))
            }
          }
        },
        { threshold: [0, 0.12] },
      )
    : null

export default {
  mounted(el, binding) {
    if (!observer) return
    el.classList.add('reveal')
    if (binding.value) el.dataset.revealDelay = `${binding.value}ms`
    observer.observe(el)
  },
  unmounted(el) {
    observer?.unobserve(el)
  },
}
