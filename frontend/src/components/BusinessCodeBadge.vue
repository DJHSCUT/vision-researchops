<script setup>
defineProps({ code: { type: String, default: '' } })
</script>

<template>
  <span class="business-code" :aria-label="`业务编号：${code || '待补齐'}`">{{ code || '编号待补齐' }}</span>
</template>

<style scoped>
.business-code {
  display: inline-block;
  max-width: 100%;
  margin-right: 8px;
  padding: 3px 7px;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  background: #f4f6f9;
  color: #788598;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.5;
  letter-spacing: .2px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}
</style>
