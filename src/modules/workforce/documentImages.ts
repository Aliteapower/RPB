export const DOCUMENT_IMAGE_LIMIT=1024*1024
/** Re-encode to strip metadata, preserve aspect ratio, and enforce the stored image bound. */
export async function prepareDocumentFile(file:File):Promise<File>{
 if(file.type==='application/pdf'){if(file.size>5*1024*1024)throw Error('largeFile');return file}
 if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>30*1024*1024)throw Error('invalidFile')
 let bitmap:ImageBitmap
 try{bitmap=await createImageBitmap(file)}catch{throw Error('invalidFile')}
 try{if(!bitmap.width||!bitmap.height||bitmap.width*bitmap.height>40_000_000)throw Error('invalidFile');let scale=Math.min(1,2200/Math.max(bitmap.width,bitmap.height));
  for(let attempt=0;attempt<8;attempt++){const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(bitmap.width*scale));canvas.height=Math.max(1,Math.round(bitmap.height*scale));const context=canvas.getContext('2d');if(!context)throw Error('invalidFile');context.fillStyle='#fff';context.fillRect(0,0,canvas.width,canvas.height);context.drawImage(bitmap,0,0,canvas.width,canvas.height);const blob=await new Promise<Blob|null>(resolve=>canvas.toBlob(resolve,'image/jpeg',Math.max(.5,.88-attempt*.055)));canvas.width=canvas.height=1;if(blob&&blob.size<=DOCUMENT_IMAGE_LIMIT)return new File([blob],file.name.replace(/\.[^.]*$/,'')+'.jpg',{type:'image/jpeg'});scale*=.78}
  throw Error('largeFile')
 }finally{bitmap.close()}
}
