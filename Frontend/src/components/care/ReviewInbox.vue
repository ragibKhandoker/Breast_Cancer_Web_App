<script setup>
import { ref, onMounted } from 'vue'
import { reviewInbox, REVIEW_STATUS } from '../../services/careService'
import { errorMessage } from '../../services/http'

// Reports patients asked this doctor to review. The review itself is written on the report page.
const requests = ref([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    requests.value = await reviewInbox()
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="inbox">
    <h2>Review requests <span v-if="requests.filter((r) => r.status === 'REQUESTED').length" class="count">{{ requests.filter((r) => r.status === 'REQUESTED').length }}</span></h2>
    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!requests.length" class="muted">No patient has asked you for a review yet.</p>
    <ul v-else class="list">
      <li v-for="r in requests" :key="r.id" class="card item" :class="{ open: r.status === 'REQUESTED' }">
        <div>
          <strong>{{ r.patientName }}</strong>
          <span class="muted small"> · {{ new Date(r.createdAt).toLocaleDateString() }}</span>
          <p v-if="r.message" class="msg">“{{ r.message }}”</p>
        </div>
        <span class="pill" :class="r.status.toLowerCase()">{{ REVIEW_STATUS[r.status] }}</span>
        <RouterLink :to="`/reports/${r.predictionId}`" class="btn btn-primary btn-sm go">{{ r.status === 'REQUESTED' ? 'Review' : 'Open' }}</RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.inbox h2 { display: flex; align-items: center; gap: 0.5rem; color: var(--purple-900); margin: 2rem 0 0.6rem; font-size: 1.3rem; }
.count { min-width: 24px; padding: 0 0.45rem; border-radius: 999px; background: #d81b6a; color: #fff; font-size: 0.8rem; line-height: 24px; text-align: center; }
.small { font-size: 0.86rem; }
.list { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.6rem; }
.item { display: grid; grid-template-columns: 1fr auto auto; align-items: center; gap: 0.8rem; padding: 0.9rem 1.1rem; }
.item.open { border-left: 4px solid #d81b6a; }
.item strong { color: var(--purple-900); }
.msg { margin: 0.3rem 0 0; color: var(--text); font-style: italic; font-size: 0.92rem; }
.pill { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.76rem; font-weight: 600; white-space: nowrap; }
.pill.requested { background: #fff4e5; color: #8a4b00; }
.pill.reviewed { background: #e8f5e9; color: #1b5e20; }
.go { background: #d81b6a; color: #fff; }
@media (max-width: 600px) { .item { grid-template-columns: minmax(0, 1fr); } }
</style>
