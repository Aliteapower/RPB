<script setup lang="ts">
import {computed,ref} from 'vue'
import {useGeneratedText} from '../../i18n/generatedText'
import DownloadableQrCode from '../../components/common/DownloadableQrCode.vue'
const props=defineProps<{store:string;registrationPath?:string}>(),{gt}=useGeneratedText(),open=ref(false)
const path=computed(()=>props.registrationPath??'/customers/register?storeId='+encodeURIComponent(props.store))
const url=computed(()=>(window.location?.origin??'')+path.value)
</script>
<template><section class="customer-registration-entry"><button type="button" :aria-expanded="open" @click="open=!open">{{gt('generated.tenant-admin-customers.056')}}</button><div v-if="open"><p>{{gt('generated.tenant-admin-customers.057')}}</p><DownloadableQrCode :value="url" :title="gt('generated.tenant-admin-customers.050')" :show-download="false" /><input :value="url" readonly :aria-label="gt('generated.tenant-admin-customers.058')"><a :href="path" target="_blank" rel="noopener noreferrer">{{gt('generated.tenant-admin-customers.059')}}</a></div></section></template>
<style scoped>
.customer-registration-entry{margin:16px 0}.customer-registration-entry>div{margin-top:12px;padding:20px;border:1px solid #d7dfea;border-radius:12px;background:white;overflow-wrap:anywhere}input{box-sizing:border-box;width:100%;padding:10px;margin-bottom:12px;font:inherit;border:1px solid #d7dfea;border-radius:8px}button{font:inherit;padding:10px 14px;border-radius:10px;border:1px solid #d7dfea;background:white;color:inherit;cursor:pointer}
</style>
