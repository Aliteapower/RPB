import test from 'node:test'
import assert from 'node:assert/strict'
import {createI18n} from 'vue-i18n'

// GHSA-83rm-6c8r-g728: attribute names must be neutralized; escaping only the
// srcdoc value is insufficient because browsers parse it as a nested document.
test('escaped HTML translations neutralize iframe srcdoc attributes',()=>{
 const instance=createI18n({legacy:false,locale:'en',escapeParameter:true,warnHtmlMessage:false,messages:{en:{probe:'<iframe srcdoc="<b>x</b>"></iframe>'}}})
 try {const rendered=instance.global.t('probe');assert.match(rendered,/<iframe/);assert.doesNotMatch(rendered,/\ssrcdoc\s*=/i)}finally{instance.dispose()}
})

test('translated payroll-style named values and plural choices remain usable',()=>{
 const instance=createI18n({legacy:false,locale:'en',messages:{en:{payment:'Paid {amount} to {name}',items:'No slips | One slip | {count} slips'}}})
 try {assert.equal(instance.global.t('payment',{amount:'SGD 2592.00',name:'Alex'}),'Paid SGD 2592.00 to Alex');assert.equal(instance.global.t('items',2),'2 slips')}finally{instance.dispose()}
})
