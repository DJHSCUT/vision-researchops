<script setup>
defineProps({ status: { type: String, default: '' } })
</script>

<template>
  <span class="status-badge" :class="{ 'status-active': status === 'ACTIVE' }"><span class="tiny-dot"></span>{{ status || 'Unknown' }}</span>
</template>
