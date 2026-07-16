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
  const currentBusinessDate = computed(() => formatBusinessDate(currentTime.value))

  return {
    currentBusinessDate,
    currentTimeText
  }
}

function formatClockTime(value: Date): string {
  return `${pad2(value.getHours())}:${pad2(value.getMinutes())}`
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

function formatBusinessDate(value: Date, timeZone = 'Asia/Singapore'): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(value)

  const part = (type: string) => parts.find(item => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}
