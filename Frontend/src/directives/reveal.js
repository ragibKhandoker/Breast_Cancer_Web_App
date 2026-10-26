/**
 * v-reveal: fades and slides an element up the first time it scrolls into view.
 * Optional value is a delay in milliseconds, used to stagger items in a row: v-reveal="150".
 * The look is defined by the .reveal / .is-visible classes in assets/main.css.
 */
const observer =
  typeof IntersectionObserver !== 'undefined'
    ? new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) {
              const el = entry.target
              el.classList.add('is-visible')
              observer.unobserve(el)
              // Drop the stagger delay once revealed, so later hover effects respond immediately.
              el.addEventListener('transitionend', () => (el.style.transitionDelay = ''), { once: true })
            }
          }
        },
        { threshold: 0.15, rootMargin: '0px 0px -40px 0px' },
      )
    : null

export default {
  mounted(el, binding) {
    if (!observer) return
    el.classList.add('reveal')
    if (binding.value) el.style.transitionDelay = `${binding.value}ms`
    observer.observe(el)
  },
  unmounted(el) {
    observer?.unobserve(el)
  },
}
