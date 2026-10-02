<script setup>
import { getStatusLabel } from '../utils/status'

defineProps({
  status: { type: String, default: '' },
  label: { type: String, default: '' },
})
</script>

<template>
  <span class="status-badge" :class="{
    'status-active': status === 'ACTIVE' || status === 'COMPLETED',
    'status-running': status === 'RUNNING',
    'status-failed': status === 'FAILED',
  }"><span class="tiny-dot"></span>{{ label || getStatusLabel(status) }}</span>
</template>
