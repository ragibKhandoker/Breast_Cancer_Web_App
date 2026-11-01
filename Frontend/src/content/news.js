// Breast cancer blogs: news reports, research and awareness events, shown on the Blogs page. Every item links to the
// original article; the summary is written from that article's own facts (each checked against the source on
// 2 November 2026). Photos are real Unsplash photos chosen for the topic (see assets/blogs/CREDITS.md), not the
// outlets' own pictures. To add one, put the newest first and keep the summary to facts the article states.
import esmoResearch from '../assets/blogs/esmo-research.jpg'
import radiotherapy from '../assets/blogs/radiotherapy.jpg'
import pinkRally from '../assets/blogs/pink-rally.jpg'
import bangladeshMother from '../assets/blogs/bangladesh-mother.jpg'
import pinkRibbon from '../assets/blogs/pink-ribbon.jpg'
import aiImaging from '../assets/blogs/ai-imaging.jpg'
import screeningReview from '../assets/blogs/screening-review.jpg'

export const NEWS_CATEGORIES = ['All', 'Bangladesh', 'Research', 'Awareness', 'Treatment']

export const NEWS = [
  {
    category: 'Bangladesh',
    outlet: 'Xinhua',
    date: '2026-10-26',
    title: 'Bangladesh gears up for major expansion of cancer care, radiotherapy facilities',
    summary: 'The government plans to buy 17 radiotherapy machines and set up a cancer institute in each of the eight divisions, bringing radiotherapy closer to district level.',
    image: radiotherapy, imageAlt: 'A radiotherapy machine in a treatment room',
    url: 'https://english.news.cn/20261026/d7ee5d6281c94be79eef52bdd1fab884/c.html',
  },
  {
    category: 'Treatment',
    outlet: 'Breastcancer.org',
    date: '2026-10-28',
    title: 'Breast cancer research highlights from ESMO 2026',
    summary: 'New results from the ESMO Congress, including a CDK4 inhibitor added to fulvestrant for HR-positive, HER2-negative metastatic breast cancer and a study in triple-negative disease that has spread to the brain.',
    image: esmoResearch, imageAlt: 'Researchers at microscopes in a cancer research laboratory',
    url: 'https://www.breastcancer.org/news/esmo-highlights-2026',
  },
  {
    category: 'Awareness',
    outlet: 'BSS',
    date: '2026-10-23',
    title: '“Pink Road Rally” begins to raise awareness on breast cancer',
    summary: 'The Bangladesh Breast Cancer Awareness Forum and more than 50 voluntary groups started a rally from Dhaka through Barishal and other southern districts to promote early detection.',
    image: pinkRally, imageAlt: 'A crowd waving pink flags at a breast cancer awareness walk',
    url: 'https://www.bssnews.net/others/437666',
  },
  {
    category: 'Bangladesh',
    outlet: 'The Business Standard',
    date: '2026-10-18',
    title: 'Govt plans grassroots breast cancer screening: Health minister',
    summary: 'At a Breast Cancer Awareness Month event in Dhaka, the health minister said screening will be taken to the grassroots. Officials noted that nearly 63% of affected women were never screened before diagnosis.',
    image: bangladeshMother, imageAlt: 'A mother holding her child in Bangladesh',
    url: 'https://www.tbsnews.net/bangladesh/health/health-minister-urges-doctors-take-breast-cancer-awareness-rural-women-1576256',
  },
  {
    category: 'Bangladesh',
    outlet: 'The Daily Star',
    date: '2026-07-01',
    title: 'The growing cancer crisis among Bangladeshi women',
    summary: 'An opinion piece estimating 13,000–15,000 new breast cancer cases among Bangladeshi women each year, with breast cancer the most common cancer in women, and calling for earlier screening.',
    image: pinkRibbon, imageAlt: 'A woman holding a pink ribbon for breast cancer awareness',
    url: 'https://www.thedailystar.net/opinion/views/news/the-growing-cancer-crisis-among-bangladeshi-women-4212731',
  },
  {
    category: 'Research',
    outlet: 'RSNA',
    date: '2026-06-09',
    title: 'AI could provide “early alert” for breast cancer six years in advance',
    summary: 'A Swedish study in Radiology found that AI scores on screening mammograms were already higher, on average, years before some women were diagnosed. It is research, not yet a clinical tool.',
    image: aiImaging, imageAlt: 'Doctors reviewing medical images on computer screens',
    url: 'https://www.rsna.org/news/2026/june/ai-flags-breast-cancer-early',
  },
  {
    category: 'Research',
    outlet: 'The Lancet (via EurekAlert!)',
    date: '2026-01-29',
    title: 'AI-supported mammography screening results in fewer aggressive and advanced breast cancers',
    summary: 'Full results of the Swedish MASAI trial of over 100,000 women: AI-supported reading found more cancers at screening and 12% fewer cancers appeared between screens, with similar false-positive rates.',
    image: screeningReview, imageAlt: 'Two doctors reviewing scans on a monitor',
    url: 'https://www.eurekalert.org/news-releases/1114399',
  },
]
