import {useStorageText} from '../text'
const zh={
 staffModeHint:"本店由门店人员直接登记存取酒，无需您扫码核销。请联系门店办理，可在“我的记录”查看存取明细。",
 quickCode:'出码取酒',quickScanHint:'商户扫码即核销',quickQuantity:'本码领取',
 scanBottle:'扫瓶身条码选酒',barcodeLabel:'商品条码',barcodeFound:'已添加',barcodeUnknown:'条码尚未收录，请填写酒名，条码将随申请提交。',wineCategory:'酒品分类',allCategories:'全部分类',previousPage:'上一页',nextPage:'下一页',pageLabel:'页码',
 bottleUnit:'瓶',bottleState:'酒瓶状态',customer:'自己带来的酒',source:'酒品来源',deposit:'我要存酒',withdrawTask:'我要取酒',records:'我的记录',
 depositHint:'选择酒品、填写瓶数并拍照，提交后由门店核实收存。',
 withdrawHint:'在酒品右侧调整瓶数并出码，到店出示给店员扫码核销。',
 recordsHint:'查看自己的寄存申请、取酒记录和已结束的寄存。',
 selectWine:'1. 选择酒品',wineSearch:'输入酒名或编号',chooseWine:'选择',noProducts:'没有找到酒品，可以填写酒名。',manualWine:'找不到酒品？填写酒名',wineName:'酒名',addManual:'添加这款酒',
 bottleDetails:'2. 填写瓶数并拍照',photoHint:'请拍摄实际要寄存的酒，方便门店核对。',addWine:'继续选择其他酒品',submitApplication:'提交寄存申请',applicationSent:'寄存申请已提交，请把酒交给门店核实收存。',rulesTitle:'寄存须知',
 emptyCollectible:'暂无可领取的酒品',emptyCollectibleHint:'新提交的寄存申请需等门店确认收存后，才会出现在这里。',chooseBottles:'1. 选择要取的酒',collectQuantity:'这次取几瓶',reviewSelection:'下一步：核对领取清单',reviewTitle:'2. 核对领取清单',generateCode:'生成取酒码',editSelection:'返回修改',selectOwn:'请先选择要取的酒品和有效瓶数',handoverHint:'请核对酒品和瓶数；商户扫描此取酒码后将直接扣除，成功后该码立即失效。',merchantHandoverHint:'扫描商户确认码仅授权本次酒品和瓶数，由商户确认实物交付后扣减。',codeCreated:'取酒码已生成，请到店向店员出示。',scanMerchant:'店员已出码？扫描确认',
 requests:'寄存申请',collections:'取酒记录',pastBottles:'已结束的寄存',noRequests:'还没有寄存申请',noCollections:'还没有取酒记录',noPastBottles:'暂无已结束的寄存',requestDetails:'查看申请明细',recover:'查询上次提交结果',pendingHint:'上一笔提交的结果还未确定，请先查询结果，避免重复提交。',
 selectionChanged:'酒品状态或数量已变化，请重新核对领取清单。',signIn:'请先登录自己的顾客账号。',
}
const en:Record<keyof typeof zh,string>={staffModeHint:"The store records bottle storage and collection directly. No customer verification is needed. Contact staff for service and view your storage history in My records.",
 quickCode:'Show pickup QR',quickScanHint:'Staff scan redeems immediately',quickQuantity:'This code collects',
 scanBottle:'Select by bottle barcode',barcodeLabel:'Product barcode',barcodeFound:'Added',barcodeUnknown:'Barcode not listed. Enter the wine name; the barcode will be included in your application.',wineCategory:'Wine category',allCategories:'All categories',previousPage:'Previous',nextPage:'Next',pageLabel:'Page',
 bottleUnit:'bottles',bottleState:'Bottle condition',customer:'Brought by me',source:'Bottle source',deposit:'Store bottles',withdrawTask:'Collect bottles',records:'My records',
 depositHint:'Choose the wine, enter bottle quantities and add photos. The store checks and accepts your bottles.',
 withdrawHint:'Set the quantity beside a bottle and show its pickup code to staff.',
 recordsHint:'View your storage applications, collections and past holdings.',
 selectWine:'1. Choose wine',wineSearch:'Search wine name or code',chooseWine:'Select',noProducts:'No matching wine. You can enter its name.',manualWine:'Cannot find it? Enter the wine name',wineName:'Wine name',addManual:'Add this wine',
 bottleDetails:'2. Add quantities and photos',photoHint:'Photograph the actual bottles so staff can check them.',addWine:'Choose more wine',submitApplication:'Submit storage application',applicationSent:'Application submitted. Hand your bottles to staff for verification.',rulesTitle:'Storage terms',
 emptyCollectible:'No bottles available to collect',emptyCollectibleHint:'New applications appear here after the store confirms physical receipt.',chooseBottles:'1. Choose bottles to collect',collectQuantity:'Bottles to collect',reviewSelection:'Next: review collection',reviewTitle:'2. Review your collection',generateCode:'Generate collection code',editSelection:'Back to selection',selectOwn:'Select your bottles and valid quantities first',handoverHint:'Check the bottles and quantities. A merchant scan deducts them immediately and invalidates this code.',merchantHandoverHint:'Scanning the merchant code authorizes these bottles. They are deducted when staff confirms handover.',codeCreated:'Your collection code is ready. Show it to staff at the store.',scanMerchant:'Staff has a code? Scan to confirm',
 requests:'Storage applications',collections:'Collections',pastBottles:'Past holdings',noRequests:'No storage applications yet',noCollections:'No collections yet',noPastBottles:'No past holdings',requestDetails:'View application details',recover:'Check previous submission',pendingHint:'The previous result is uncertain. Check it before submitting again.',
 selectionChanged:'Bottle availability changed. Review your selection again.',signIn:'Sign in to your customer account first.',
}
export function useCustomerStorageText(){const base=useStorageText();return{locale:base.locale,t:(key:string)=>(base.locale.value.startsWith('en')?en:zh)[key as keyof typeof zh]??base.t(key)}}
