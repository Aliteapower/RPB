<script setup lang="ts">
import {computed,watch} from 'vue'
import {useStorageText} from './text'
const props=defineProps<{modelValue:number;max:number;disabled:boolean;label:string}>(),emit=defineEmits<{'update:modelValue':[number]}>(),{t}=useStorageText()
const maximum=computed(()=>Math.max(1,Math.trunc(props.max))),value=computed(()=>normalize(props.modelValue))
function normalize(input:number){return Math.min(maximum.value,Math.max(1,Number.isFinite(input)?Math.trunc(input):1))}
function update(input:number){if(!props.disabled)emit('update:modelValue',normalize(input))}
watch([()=>props.modelValue,maximum],()=>{if(props.modelValue!==value.value)emit('update:modelValue',value.value)},{immediate:true})
</script>
<template>
 <div class="storage-quantity-field"><span>{{label}}</span><div class="storage-quantity" role="group" :aria-label="label">
  <button type="button" :aria-label="t('decreaseQuantity')" :disabled="disabled||value<=1" @click="update(value-1)">−</button>
  <input type="number" inputmode="numeric" :aria-label="label" :value="value" min="1" :max="maximum" step="1" :disabled="disabled||maximum===1" @input="update(Number(($event.target as HTMLInputElement).value))">
  <button type="button" :aria-label="t('increaseQuantity')" :disabled="disabled||value>=maximum" @click="update(value+1)">+</button>
 </div></div>
</template>
