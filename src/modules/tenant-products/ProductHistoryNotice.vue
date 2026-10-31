<script setup lang="ts">
import {useI18n} from 'vue-i18n'
const {t}=useI18n()
</script><template><p class="product-history-notice" role="status">{{t('tenantProducts.closedHistory')}}</p></template><style scoped>.product-history-notice{padding:12px 16px;background:#eef6f5;border-radius:8px;color:#0f766e}</style>
