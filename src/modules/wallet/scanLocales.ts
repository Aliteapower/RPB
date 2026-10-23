export const walletScannerZh = {
  title:'对准钱包二维码', aim:'将二维码放入框内，保持清晰；识别后自动关闭摄像头。',
  idle:'摄像头未打开', opening:'正在打开摄像头，请在浏览器提示中允许访问', scanning:'正在扫描', paused:'摄像头已暂停，点击打开后继续',
  recognized:'二维码已识别，等待校验', processing:'正在校验钱包二维码…', retry:'重新打开摄像头', switch:'切换前后摄像头', torchOn:'打开补光', torchOff:'关闭补光',
  alternatives:'相册或粘贴二维码', privacy:'画面仅在本机识别，不上传图片；扫码不会直接扣款。', wrongCustomer:'请扫描顾客的钱包付款码', wrongBill:'请扫描商户消费付款码或积分兑换码',
  imageError:'未识别到清晰的钱包码，请换一张二维码图片或使用摄像头。', imageLarge:'图片太大，请选择10MB以内的清晰二维码图片。',
  issues:{insecure:'摄像头需要HTTPS安全连接。请使用正式钱包网址，或从相册识别。', unsupported:'当前浏览器无法提供摄像头，请在Safari或Chrome打开钱包，或使用相册识别。', denied:'摄像头权限未获允许。请在浏览器或手机设置中允许相机访问，再点击重新打开；微信内可尝试在Safari或Chrome打开。', missing:'未找到可用摄像头，请检查相机，或从相册识别。', busy:'摄像头正被占用或无法启动。请关闭其他相机/扫码应用后重试。', playback:'摄像头预览未能播放，请点击重新打开。iPhone请保持页面在前台。', interrupted:'摄像头被系统中断，请点击重新打开继续扫码。', decode:'无法读取当前画面，请重新打开摄像头或从相册识别。', torch:'此摄像头无法开启补光，仍可继续扫码。'}
}
export const walletScannerEn = {
  title:'Align the wallet QR code', aim:'Keep the code clear inside the frame. The camera closes after recognition.',
  idle:'Camera is off', opening:'Opening camera. Allow camera access in your browser prompt.', scanning:'Scanning', paused:'Camera paused. Open it again to continue.',
  recognized:'Code recognized. Awaiting verification.', processing:'Verifying wallet QR code…', retry:'Reopen camera', switch:'Switch front / rear camera', torchOn:'Turn light on', torchOff:'Turn light off',
  alternatives:'Photo or pasted code', privacy:'Images are decoded on this device and are not uploaded. Scanning does not debit the wallet.', wrongCustomer:'Scan the customer’s wallet payment code.', wrongBill:'Scan a merchant payment or points redemption code.',
  imageError:'No clear wallet QR code found. Choose another image or use the camera.', imageLarge:'Choose a clear QR image smaller than 10MB.',
  issues:{insecure:'Camera access needs HTTPS. Use the official wallet address or choose a QR image.', unsupported:'This browser cannot provide camera access. Open the wallet in Safari or Chrome, or choose an image.', denied:'Camera access was not allowed. Enable camera access in browser or phone settings, then reopen. In WeChat, try opening in Safari or Chrome.', missing:'No camera was found. Check the camera or choose a QR image.', busy:'The camera is busy or could not start. Close other camera or scanner apps, then retry.', playback:'The preview could not play. Reopen the camera and keep this page in the foreground on iPhone.', interrupted:'The camera was interrupted. Reopen it to continue scanning.', decode:'The camera image could not be read. Reopen the camera or choose a QR image.', torch:'This camera could not enable its light. Scanning can continue.'}
}
export const walletFeedbackZh = {
  paidStaff:'收款成功', paidCustomer:'付款成功', partial:'钱包已扣款，仍需补付', unfunded:'钱包未扣款，仍需补付',
  settled:'本单已结清，合计已收 {amount}', split:'钱包已扣 {wallet}，仍需补付 {due}', zero:'钱包未扣款，仍需支付 {due}', dismiss:'关闭收款提示'
}
export const walletFeedbackEn = {
  paidStaff:'Payment received', paidCustomer:'Payment complete', partial:'Wallet debited. Balance still due.', unfunded:'No wallet debit. Balance still due.',
  settled:'Bill settled. Total received: {amount}', split:'Wallet debited {wallet}. Still due: {due}', zero:'No wallet debit. Still due: {due}', dismiss:'Dismiss payment notice'
}
