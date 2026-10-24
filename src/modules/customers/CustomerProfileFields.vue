<script setup lang="ts">
import CountryPhoneField from '../../components/common/CountryPhoneField.vue'
import {useGeneratedText} from '../../i18n/generatedText'
import {useI18n} from 'vue-i18n'
import type {CustomerPrivacyNotice} from '../../api/tenantAdminApi'
defineProps<{form:{displayName:string;nickname:string;phoneE164:string;email:string};disabled?:boolean;showPrivacy?:boolean;selfRegistration?:boolean;privacyNotice?:CustomerPrivacyNotice|null;noticeLoading?:boolean}>()
const privacyAccepted=defineModel<boolean>('privacyAccepted',{default:true})
defineEmits<{'reload-notice':[]}>()
const {gt}=useGeneratedText()
const {locale,t}=useI18n()
</script>
<template><fieldset class="customer-profile-fields" :disabled="disabled">
 <label><span>{{gt('generated.tenant-admin-customers.009')}} <small>{{gt('generated.tenant-admin-customers.023')}}</small></span><input v-model.trim="form.displayName" name="displayName" autocomplete="name"></label>
 <label><span>{{gt('generated.tenant-admin-customers.010')}} <small>{{gt('generated.tenant-admin-customers.023')}}</small></span><select v-model="form.nickname" name="nickname"><option value="">{{gt('generated.tenant-admin-customers.023')}}</option><option value="先生">{{gt('generated.tenant-admin-customers.024')}}</option><option value="女士">{{gt('generated.tenant-admin-customers.025')}}</option></select></label>
 <CountryPhoneField v-model="form.phoneE164" :label="gt('generated.tenant-admin-customers.011')" name="phoneE164" model-format="e164" :disabled="disabled" required />
 <label><span>{{gt('generated.tenant-admin-customers.012')}} <small>{{gt('generated.tenant-admin-customers.023')}}</small></span><input v-model.trim="form.email" name="email" type="email" autocomplete="email"></label>
 <section v-if="showPrivacy" class="customer-privacy-notice"><p v-if="noticeLoading">{{t('common.loading')}}</p><template v-else-if="privacyNotice"><p>{{locale==='en-SG'?privacyNotice.textEn:privacyNotice.textZh}}</p><label class="privacy-check"><input v-model="privacyAccepted" type="checkbox" required :disabled="disabled"><span>{{locale==='en-SG'?privacyNotice.labelEn:privacyNotice.labelZh}}</span></label><small>{{privacyNotice.version}} · {{gt(selfRegistration?'generated.tenant-admin-customers.060':'generated.tenant-admin-customers.045')}}</small></template><template v-else><p role="alert">{{gt('generated.tenant-admin-customers.048')}}</p><button type="button" @click="$emit('reload-notice')">{{t('common.actions.refresh')}}</button></template></section>
</fieldset></template>
<style scoped>
.customer-profile-fields{border:0;padding:0;margin:0;min-width:0;display:grid;gap:16px}
label{display:grid;gap:6px;color:inherit;font-weight:600}label>span{display:flex;justify-content:space-between;gap:8px}small{color:#64748b;font-size:12px;font-weight:400}
input,select{box-sizing:border-box;min-width:0;width:100%;min-height:44px;padding:10px 12px;border:1px solid #d7dfea;border-radius:var(--customer-field-radius,6px);background:white;color:inherit;font:inherit}
.customer-privacy-notice{padding:14px;border:1px solid #d7dfea;border-radius:var(--customer-field-radius,6px);font-size:13px;overflow-wrap:anywhere}.customer-privacy-notice p{margin:0 0 12px}.privacy-check{display:flex;align-items:flex-start;gap:10px;margin:10px 0}.privacy-check input{flex:0 0 20px;width:20px;min-height:20px;margin:0}.privacy-check span{display:block}
</style>
