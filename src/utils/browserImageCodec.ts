export interface UploadImage {width:number;height:number;source:CanvasImageSource;close:()=>void}
export interface ImageUploadCodec {decode:(file:File)=>Promise<UploadImage>;encode:(image:UploadImage,width:number,height:number,type:string,quality?:number)=>Promise<Blob>}
/** Browser decoders apply camera orientation; canvases strip metadata and are released after encoding. */
export const browserImageCodec:ImageUploadCodec={
 async decode(file){
  if(typeof createImageBitmap==='function'){try{const image=await createImageBitmap(file,{imageOrientation:'from-image'});return{width:image.width,height:image.height,source:image,close:()=>image.close()}}catch{/* Older WebKit falls back to its native image decoder. */}}
  const url=URL.createObjectURL(file),image=new Image()
  try{await new Promise<void>((resolve,reject)=>{image.onload=()=>resolve();image.onerror=()=>reject(Error('decode'));image.src=url});return{width:image.naturalWidth,height:image.naturalHeight,source:image,close:()=>{image.src='';URL.revokeObjectURL(url)}}}
  catch(e){URL.revokeObjectURL(url);throw e}
 },
 async encode(image,width,height,type,quality){
  const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height
  try{const context=canvas.getContext('2d');if(!context)throw Error('canvas');if(type==='image/jpeg'){context.fillStyle='#fff';context.fillRect(0,0,width,height)}context.drawImage(image.source,0,0,width,height)
   const blob=await new Promise<Blob|null>((resolve,reject)=>{try{canvas.toBlob(resolve,type,quality)}catch(e){reject(e)}});if(!blob||blob.type!==type)throw Error('encode');return blob
  }finally{canvas.width=canvas.height=1}
 }
}
