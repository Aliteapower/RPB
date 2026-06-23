<template>
  <section class="workflow-strip" aria-label="门店流转提示">
    <span>散客入座</span>
    <span class="workflow-arrow" aria-hidden="true">-&gt;</span>
    <span>占用</span>
    <span class="workflow-arrow" aria-hidden="true">-&gt;</span>
    <span>清台</span>
    <span class="workflow-arrow" aria-hidden="true">-&gt;</span>
    <span>可用</span>
  </section>
</template>

<style scoped>
.workflow-strip {
  align-items: center;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  color: #9a3412;
  display: flex;
  flex-wrap: wrap;
  font-size: 0.82rem;
  font-weight: 900;
  gap: 7px;
  justify-content: center;
  padding: 10px 12px;
}

.workflow-arrow {
  color: #94a3b8;
  font-weight: 800;
}
</style>
