<script setup>
import { computed } from 'vue'
import {
  ArrowRight, Award, Bell, Bookmark, BookOpen, Building2, CalendarDays, CircleAlert, CircleCheck, Clock, Copy, FileText,
  Heart, Image, Languages, Mail, MapPin, Menu, MessageCircle, Microscope, Phone, RefreshCw, Search, Send, ShieldCheck,
  Trash2, Upload, User, Users, Wallet, X,
} from 'lucide-vue-next'

// Icons come from Lucide (lucide.dev, ISC licence), the open-source set used by many professional sites, through
// its official Vue package. Brand logos come from Simple Icons (simpleicons.org, CC0), the official brand marks.
// Names stay the same as before, so every page keeps working; colour follows `currentColor`.
const LUCIDE = {
  'arrow-right': ArrowRight,
  search: Search,
  menu: Menu,
  close: X,
  'shield-check': ShieldCheck,
  book: BookOpen,
  bookmark: Bookmark,
  heart: Heart,
  users: Users,
  microscope: Microscope,
  calendar: CalendarDays,
  'file-text': FileText,
  phone: Phone,
  mail: Mail,
  upload: Upload,
  image: Image,
  'check-circle': CircleCheck,
  'alert-circle': CircleAlert,
  refresh: RefreshCw,
  message: MessageCircle,
  send: Send,
  copy: Copy,
  bell: Bell,
  trash: Trash2,
  clock: Clock,
  award: Award,
  building: Building2,
  user: User,
  wallet: Wallet,
  'map-pin': MapPin,
  languages: Languages,
}

// Simple Icons 16.36.0 (CC0-1.0), filled 24x24 paths.
const BRANDS = {
  facebook: 'M9.101 23.691v-7.98H6.627v-3.667h2.474v-1.58c0-4.085 1.848-5.978 5.858-5.978.401 0 .955.042 1.468.103a8.68 8.68 0 0 1 1.141.195v3.325a8.623 8.623 0 0 0-.653-.036 26.805 26.805 0 0 0-.733-.009c-.707 0-1.259.096-1.675.309a1.686 1.686 0 0 0-.679.622c-.258.42-.374.995-.374 1.752v1.297h3.919l-.386 2.103-.287 1.564h-3.246v8.245C19.396 23.238 24 18.179 24 12.044c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.628 3.874 10.35 9.101 11.647Z',
  youtube: 'M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z',
  linkedin: 'M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433c-1.144 0-2.063-.926-2.063-2.065 0-1.138.92-2.063 2.063-2.063 1.14 0 2.064.925 2.064 2.063 0 1.139-.925 2.065-2.064 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z',
  // X (formerly Twitter).
  twitter: 'M14.234 10.162 22.977 0h-2.072l-7.591 8.824L7.251 0H.258l9.168 13.343L.258 24H2.33l8.016-9.318L16.749 24h6.993zm-2.837 3.299-.929-1.329L3.076 1.56h3.182l5.965 8.532.929 1.329 7.754 11.09h-3.182z',
}

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 24 },
  strokeWidth: { type: [Number, String], default: 2 },
  filled: { type: Boolean, default: false },
})

const lucide = computed(() => LUCIDE[props.name])
const brand = computed(() => BRANDS[props.name])
</script>

<template>
  <component
    :is="lucide"
    v-if="lucide"
    :size="size"
    :stroke-width="strokeWidth"
    :fill="filled ? 'currentColor' : 'none'"
    aria-hidden="true"
    focusable="false"
  />
  <svg v-else-if="brand" :width="size" :height="size" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
    <path :d="brand" />
  </svg>
</template>
