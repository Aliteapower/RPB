<script setup lang="ts">
import {ref,nextTick,watch,onUnmounted} from 'vue'
import type {StorageApi} from './api'
import {useStorageText} from './text'
const props=defineProps<{photos:string[];api:StorageApi}>(),{t}=useStorageText(),active=ref<string|null>(null),zoomed=ref(false),closeButton=ref<HTMLButtonElement|null>(null)
let opener:HTMLElement|null=null
function close(){active.value=null;zoomed.value=false;opener?.focus?.();opener=null}
function trap(event:KeyboardEvent){const controls=Array.from((event.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('button,a[href]'));const index=controls.indexOf(event.target as HTMLElement);controls[(index+(event.shiftKey?-1:1)+controls.length)%controls.length]?.focus()}
async function open(photo:string,event:MouseEvent){opener=event.currentTarget as HTMLElement;active.value=photo;zoomed.value=false;await nextTick();closeButton.value?.focus?.()}
watch(()=>props.api.base,close)
onUnmounted(()=>{active.value=null;opener?.focus?.()})
</script>
<template><div class="storage-photos"><button v-for="photo in photos" :key="photo" type="button" class="storage-photo-button" :aria-label="t('enlargePhoto')" @click="open(photo,$event)"><img :src="`${api.base}/photos/${photo}`" :alt="t('photos')" loading="lazy"></button></div>
 <div v-if="active" class="storage-lightbox" role="dialog" aria-modal="true" :aria-label="t('photoPreview')" @keydown.esc.prevent="close" @keydown.tab.prevent="trap" @click.self="close"><div class="storage-lightbox-toolbar"><button ref="closeButton" type="button" @click="close">{{t('closePhoto')}}</button><button type="button" :aria-pressed="zoomed" @click="zoomed=!zoomed">{{t(zoomed?'fitPhoto':'zoomPhoto')}}</button><a :href="`${api.base}/photos/${active}`" target="_blank" rel="noopener">{{t('originalPhoto')}}</a></div><div class="storage-lightbox-image" :class="{'is-zoomed':zoomed}" @click.self="close"><img :src="`${api.base}/photos/${active}`" :alt="t('photos')" @dblclick="zoomed=!zoomed"></div></div>
</template>
