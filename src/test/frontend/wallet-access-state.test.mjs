import {test} from 'node:test'
import assert from 'node:assert/strict'
import {walletHistoryOnly,walletNeedsSetup} from '../../modules/wallet/accessState.ts'
test('missing program and disabled local points are setup states, not a closed product',()=>{
 assert.equal(walletHistoryOnly({transactionsEnabled:false,program:null,denialReason:null}),false)
 assert.equal(walletHistoryOnly({transactionsEnabled:false,program:null,denialReason:'WALLET_POINTS_DISABLED'}),false)
 assert.equal(walletNeedsSetup({transactionsEnabled:false,program:null,denialReason:null}),true)
 assert.equal(walletNeedsSetup({transactionsEnabled:false,program:null,denialReason:'WALLET_POINTS_DISABLED'}),false)
})
test('explicit history and every commercial denial remain read-only',()=>{
 for(const denialReason of ['STORE_APP_NOT_ENABLED','STORE_APP_EXPIRED','TENANT_APP_NOT_ENABLED','TENANT_APP_EXPIRED','APP_DISABLED','UNKNOWN_DENIAL'])assert.equal(walletHistoryOnly({transactionsEnabled:false,denialReason}),true)
 assert.equal(walletHistoryOnly({transactionsEnabled:true},true),true)
 assert.equal(walletHistoryOnly(null),false)
 assert.equal(walletHistoryOnly(null,true),true)
 assert.equal(walletNeedsSetup({transactionsEnabled:false,program:null,denialReason:'STORE_APP_NOT_ENABLED'}),false)
})
