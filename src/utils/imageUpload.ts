import {browserImageCodec,type ImageUploadCodec} from './browserImageCodec'
export const IMAGE_UPLOAD_LIMIT=1_000_000
export const IMAGE_SOURCE_LIMIT=80*1024*1024
const supported=new Set(['image/jpeg','image/png','image/webp'])
export class ImageUploadError extends Error{
 constructor(readonly code:'type'|'source'|'decode'|'limit'){
  const en=typeof document!=='undefined'&&document.documentElement?.lang?.startsWith('en'),messages={type:['请选择JPG、PNG或WebP图片。','Choose a JPG, PNG or WebP image.'],source:['图片为空、过大或无法处理，请选择其他图片。','The image is empty or too large to process. Choose another image.'],decode:['图片压缩失败，请更换图片后重试。','Image compression failed. Choose another image and retry.'],limit:['图片无法压缩到1MB以内，请更换图片。','The image could not be reduced to 1MB. Choose another image.']}
  super(messages[code][en?1:0]);this.name='ImageUploadError'
 }
}
interface Options {codec?:ImageUploadCodec;forceReencode?:boolean;format?:'image/jpeg';maxEdge?:number}
/** Every successful result is at most 1,000,000 bytes; never fall back to an oversized original. */
export async function prepareImageUpload(file:File,options:Options={}):Promise<File>{
 if(!supported.has(file.type))throw new ImageUploadError('type')
 if(file.size<=0||file.size>IMAGE_SOURCE_LIMIT)throw new ImageUploadError('source')
 if(file.size<=IMAGE_UPLOAD_LIMIT&&!options.forceReencode)return file
 const codec=options.codec??browserImageCodec
 let image:Awaited<ReturnType<ImageUploadCodec['decode']>>
 try{image=await codec.decode(file)}catch{throw new ImageUploadError('decode')}
 try{
  if(!image.width||!image.height||image.width*image.height>64_000_000)throw new ImageUploadError('source')
  const type=options.format??(file.type==='image/jpeg'?'image/jpeg':'image/png'),initial=Math.min(1,(options.maxEdge??2560)/Math.max(image.width,image.height)),qualities=type==='image/jpeg'?[.90,.82,.74,.66,.58]:[undefined]
  for(let resize=0;resize<12;resize++){
   const scale=initial*Math.pow(.8,resize),width=Math.max(1,Math.round(image.width*scale)),height=Math.max(1,Math.round(image.height*scale))
   for(const quality of qualities){const blob=await codec.encode(image,width,height,type,quality);if(blob.size>0&&blob.size<=IMAGE_UPLOAD_LIMIT){const name=(file.name.replace(/\.[^.]*$/,'')||'image')+(type==='image/jpeg'?'.jpg':'.png');return new File([blob],name,{type,lastModified:file.lastModified})}}
   if(Math.max(width,height)<=96)break
  }
  throw new ImageUploadError('limit')
 }catch(e){if(e instanceof ImageUploadError)throw e;throw new ImageUploadError('decode')}
 finally{image.close()}
}
/** Process file parts only; business fields, repeated parts and non-image uploads are preserved. */
export async function prepareImageFormData(body:FormData,options:Options={}):Promise<FormData>{
 const parts=[...body.entries()],images=parts.some(([,value])=>typeof value!=='string'&&value.type.startsWith('image/'))
 if(!images)return body
 const result=new FormData()
 for(const [key,value] of parts)result.append(key,typeof value!=='string'&&value.type.startsWith('image/')?await prepareImageUpload(value,options):value)
 return result
}
