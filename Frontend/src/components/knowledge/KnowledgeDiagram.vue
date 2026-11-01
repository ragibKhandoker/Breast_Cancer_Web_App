<script setup>
import { ref } from 'vue'

// Labelled teaching diagrams drawn in SVG. They are simplified on purpose and are not to scale.
// The TNM diagram is interactive: choosing T, N or M highlights that part.
defineProps({ name: { type: String, required: true } })

const TNM = [
  { key: 'T', title: 'T: Primary tumour', text: 'How large the main tumour is, and whether it has grown into the skin or chest wall.' },
  { key: 'N', title: 'N: Regional lymph nodes', text: 'Whether cancer has reached nearby lymph nodes, such as those under the arm.' },
  { key: 'M', title: 'M: Distant metastasis', text: 'Whether cancer has spread to distant organs, such as the bones, liver, lungs or brain.' },
]
const tnm = ref('T')
</script>

<template>
  <figure class="diagram">
    <!-- Side view of the breast -->
    <svg v-if="name === 'anatomy'" viewBox="0 0 420 260" role="img" aria-labelledby="anatomy-title">
      <title id="anatomy-title">Side view of the breast showing lobules, ducts, nipple, fatty tissue, chest muscle, ribs and lymph nodes</title>
      <rect x="20" y="20" width="26" height="220" rx="6" fill="#f3d4e3" />
      <g fill="#fbe4ec" stroke="#e8b4c8" stroke-width="2"><rect x="50" y="30" width="22" height="200" rx="8" /></g>
      <path d="M72 40 C150 40 230 90 250 130 C230 170 150 220 72 220 Z" fill="#fff4e8" stroke="#e84a8a" stroke-width="2.5" />
      <g stroke="#6b3fb5" stroke-width="2.2" fill="none">
        <path d="M110 80 C150 95 200 115 246 130" /><path d="M110 130 C160 130 200 130 246 130" /><path d="M110 180 C150 165 200 145 246 130" />
      </g>
      <g fill="#f7a8c8" stroke="#d81b6a" stroke-width="1.5">
        <circle cx="106" cy="76" r="9" /><circle cx="98" cy="88" r="7" /><circle cx="104" cy="130" r="9" /><circle cx="96" cy="142" r="7" />
        <circle cx="106" cy="184" r="9" /><circle cx="98" cy="172" r="7" />
      </g>
      <circle cx="252" cy="130" r="7" fill="#d81b6a" />
      <g fill="#a99be0"><circle cx="150" cy="30" r="6" /><circle cx="168" cy="26" r="5" /></g>
      <g font-size="12" fill="#2a1650" font-family="Poppins, sans-serif">
        <text x="290" y="70">Lobules (make milk)</text><path d="M286 66 L112 76" stroke="#b9a5d9" />
        <text x="290" y="110">Ducts (carry milk)</text><path d="M286 106 L200 116" stroke="#b9a5d9" />
        <text x="290" y="135">Nipple</text><path d="M286 131 L260 130" stroke="#b9a5d9" />
        <text x="290" y="175">Fatty tissue</text><path d="M286 171 L180 170" stroke="#b9a5d9" />
        <text x="290" y="215">Chest muscle</text><path d="M286 211 L64 210" stroke="#b9a5d9" />
        <text x="290" y="245">Ribs</text><path d="M286 241 L34 236" stroke="#b9a5d9" />
        <text x="150" y="16">Lymph nodes (towards the armpit)</text>
      </g>
    </svg>

    <!-- Normal and uncontrolled cell growth -->
    <svg v-else-if="name === 'cell-growth'" viewBox="0 0 420 200" role="img" aria-labelledby="cells-title">
      <title id="cells-title">Normal cells divide in an orderly way and old cells die; cancer cells keep dividing and pile up</title>
      <g font-size="13" fill="#2a1650" font-family="Poppins, sans-serif" font-weight="600">
        <text x="40" y="24">Normal cells</text><text x="250" y="24">Cancer cells</text>
      </g>
      <g stroke="#6b3fb5" stroke-width="2" fill="#efeafd">
        <circle cx="60" cy="70" r="16" /><circle cx="110" cy="60" r="16" /><circle cx="110" cy="100" r="16" />
        <circle cx="160" cy="80" r="16" /><circle cx="70" cy="140" r="16" />
      </g>
      <circle cx="160" cy="140" r="14" fill="none" stroke="#b9a5d9" stroke-width="2" stroke-dasharray="4 4" />
      <g stroke="#d81b6a" stroke-width="2" fill="#fde3ef">
        <circle cx="260" cy="70" r="15" /><circle cx="290" cy="58" r="18" /><circle cx="300" cy="92" r="14" /><circle cx="330" cy="70" r="17" />
        <circle cx="270" cy="110" r="17" /><circle cx="320" cy="122" r="16" /><circle cx="355" cy="100" r="15" /><circle cx="295" cy="145" r="18" />
        <circle cx="350" cy="148" r="14" /><circle cx="250" cy="150" r="13" />
      </g>
      <g font-size="11" fill="#6f6787" font-family="Poppins, sans-serif">
        <text x="30" y="185">Divide when needed; old cells die</text>
        <text x="235" y="185">Keep dividing and form a tumour</text>
      </g>
    </svg>

    <!-- Receptors on a breast cancer cell -->
    <svg v-else-if="name === 'receptors'" viewBox="0 0 420 220" role="img" aria-labelledby="rec-title">
      <title id="rec-title">A breast cancer cell with estrogen receptors, progesterone receptors and HER2 proteins; Ki-67 shows dividing cells</title>
      <circle cx="150" cy="110" r="80" fill="#fff4f9" stroke="#e84a8a" stroke-width="3" />
      <circle cx="150" cy="110" r="28" fill="#efeafd" stroke="#6b3fb5" stroke-width="2" />
      <text x="128" y="114" font-size="11" fill="#2a1650" font-family="Poppins, sans-serif">Ki-67</text>
      <g fill="#6b3fb5"><rect x="140" y="22" width="8" height="18" rx="3" /><rect x="225" y="100" width="18" height="8" rx="3" /></g>
      <g fill="#d81b6a"><rect x="80" y="45" width="8" height="18" rx="3" transform="rotate(-40 84 54)" /><rect x="70" y="160" width="8" height="18" rx="3" transform="rotate(40 74 169)" /></g>
      <g fill="#2e7d32"><path d="M200 40 l8 -14 l8 14 z" /><path d="M210 170 l8 14 l8 -14 z" /></g>
      <g font-size="12" fill="#2a1650" font-family="Poppins, sans-serif">
        <rect x="270" y="40" width="12" height="12" rx="2" fill="#6b3fb5" /><text x="290" y="51">ER (estrogen receptor)</text>
        <rect x="270" y="75" width="12" height="12" rx="2" fill="#d81b6a" /><text x="290" y="86">PR (progesterone receptor)</text>
        <path d="M270 122 l6 -12 l6 12 z" fill="#2e7d32" /><text x="290" y="121">HER2 protein</text>
        <circle cx="276" cy="152" r="6" fill="#efeafd" stroke="#6b3fb5" /><text x="290" y="156">Ki-67: dividing cells</text>
      </g>
    </svg>

    <!-- Tumour grade -->
    <svg v-else-if="name === 'grade'" viewBox="0 0 420 170" role="img" aria-labelledby="grade-title">
      <title id="grade-title">Grade 1 cells look close to normal, grade 2 somewhat abnormal, grade 3 very abnormal</title>
      <g v-for="(g, i) in [1, 2, 3]" :key="g" :transform="`translate(${20 + i * 135} 20)`">
        <rect width="115" height="110" rx="12" fill="#fff" stroke="#f3d4e3" stroke-width="2" />
        <g v-if="g === 1" fill="#efeafd" stroke="#6b3fb5" stroke-width="1.5">
          <circle cx="30" cy="35" r="12" /><circle cx="60" cy="35" r="12" /><circle cx="90" cy="35" r="12" />
          <circle cx="30" cy="70" r="12" /><circle cx="60" cy="70" r="12" /><circle cx="90" cy="70" r="12" />
        </g>
        <g v-else-if="g === 2" fill="#fde3ef" stroke="#e84a8a" stroke-width="1.5">
          <circle cx="28" cy="32" r="11" /><circle cx="58" cy="38" r="14" /><circle cx="92" cy="30" r="10" />
          <circle cx="34" cy="72" r="14" /><circle cx="66" cy="76" r="10" /><circle cx="92" cy="68" r="13" />
        </g>
        <g v-else fill="#f7a8c8" stroke="#d81b6a" stroke-width="1.5">
          <ellipse cx="26" cy="30" rx="14" ry="9" /><circle cx="56" cy="40" r="17" /><ellipse cx="94" cy="28" rx="9" ry="14" />
          <circle cx="30" cy="76" r="10" /><ellipse cx="66" cy="80" rx="18" ry="11" /><circle cx="98" cy="72" r="8" /><circle cx="84" cy="52" r="7" />
        </g>
        <text x="57" y="132" text-anchor="middle" font-size="13" font-weight="600" fill="#2a1650" font-family="Poppins, sans-serif">Grade {{ g }}</text>
        <text x="57" y="148" text-anchor="middle" font-size="10.5" fill="#6f6787" font-family="Poppins, sans-serif">{{ ['Close to normal', 'Somewhat abnormal', 'Very abnormal'][i] }}</text>
      </g>
    </svg>

    <!-- TNM -->
    <div v-else-if="name === 'tnm'" class="tnm">
      <div class="tnm-buttons" role="group" aria-label="TNM parts">
        <button v-for="part in TNM" :key="part.key" type="button" :class="{ active: tnm === part.key }"
          :aria-pressed="tnm === part.key" @click="tnm = part.key">{{ part.key }}</button>
      </div>
      <svg viewBox="0 0 420 220" role="img" aria-labelledby="tnm-title">
        <title id="tnm-title">TNM: the tumour in the breast, lymph nodes under the arm, and distant organs</title>
        <path d="M150 30 C150 30 120 60 120 120 C120 180 190 200 230 170 C260 150 250 90 220 60" fill="#fff4e8" stroke="#e8b4c8" stroke-width="2.5" />
        <circle cx="190" cy="130" r="18" :fill="tnm === 'T' ? '#d81b6a' : '#f7a8c8'" :stroke="tnm === 'T' ? '#2a1650' : 'none'" stroke-width="3" />
        <g :fill="tnm === 'N' ? '#6b3fb5' : '#d9cff0'" :stroke="tnm === 'N' ? '#2a1650' : 'none'" stroke-width="2">
          <circle cx="105" cy="55" r="9" /><circle cx="92" cy="74" r="8" /><circle cx="110" cy="86" r="7" />
        </g>
        <path d="M180 118 C150 100 130 90 112 80" :stroke="tnm === 'N' ? '#6b3fb5' : '#d9cff0'" stroke-width="2" fill="none" stroke-dasharray="5 4" />
        <g :opacity="tnm === 'M' ? 1 : 0.35">
          <path d="M205 120 C260 90 300 70 330 60" stroke="#2e7d32" stroke-width="2" fill="none" stroke-dasharray="5 4" />
          <rect x="320" y="36" width="70" height="40" rx="10" fill="#e8f5e9" stroke="#2e7d32" stroke-width="2" />
          <text x="355" y="61" text-anchor="middle" font-size="11" fill="#1b5e20" font-family="Poppins, sans-serif">Distant organ</text>
        </g>
        <g font-size="12" fill="#2a1650" font-family="Poppins, sans-serif">
          <text x="215" y="160">Tumour (T)</text><text x="40" y="40">Lymph nodes (N)</text>
        </g>
      </svg>
      <p class="tnm-text" aria-live="polite"><strong>{{ TNM.find((p) => p.key === tnm).title }}.</strong> {{ TNM.find((p) => p.key === tnm).text }}</p>
    </div>
    <figcaption>Simplified teaching diagram, not to scale.</figcaption>
  </figure>
</template>

<style scoped>
.diagram { margin: 0; padding: 1rem; border: 1px solid #f3d4e3; border-radius: 16px; background: #fff; }
.diagram svg { width: 100%; height: auto; max-height: 300px; }
figcaption { margin-top: 0.5rem; font-size: 0.8rem; color: #6f6787; text-align: center; }
.tnm-buttons { display: flex; gap: 0.5rem; justify-content: center; margin-bottom: 0.5rem; }
.tnm-buttons button {
  width: 46px; height: 40px; border-radius: 10px; border: 2px solid #f3d4e3; background: #fff;
  color: #2a1650; font: 700 1.05rem 'Poppins', sans-serif; cursor: pointer;
}
.tnm-buttons button.active { background: #d81b6a; border-color: #d81b6a; color: #fff; }
.tnm-buttons button:focus-visible { outline: 3px solid #f7a8c8; outline-offset: 2px; }
.tnm-text { margin: 0.5rem 0 0; text-align: center; color: #3d3557; line-height: 1.6; }
</style>
