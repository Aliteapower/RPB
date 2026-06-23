import { computed, onMounted, onUnmounted, ref } from 'vue'

export function useCurrentClock() {
  const currentTime = ref(new Date())
  let timerId: number | undefined

  onMounted(() => {
    timerId = window.setInterval(() => {
      currentTime.value = new Date()
    }, 30000)
  })

  onUnmounted(() => {
    if (timerId !== undefined) {
      window.clearInterval(timerId)
    }
  })

  const currentTimeText = computed(() => formatClockTime(currentTime.value))

  return {
    currentTimeText
  }
}

function formatClockTime(value: Date): string {
  return `${pad2(value.getHours())}:${pad2(value.getMinutes())}`
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}
