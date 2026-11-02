import {IMAGE_UPLOAD_LIMIT,prepareImageUpload} from '../../utils/imageUpload'
export const DOCUMENT_IMAGE_LIMIT=IMAGE_UPLOAD_LIMIT
export async function prepareDocumentFile(file:File):Promise<File>{
 if(file.type==='application/pdf'){if(file.size>5*1024*1024)throw Error('largeFile');return file}
 if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>30*1024*1024)throw Error('invalidFile')
 try{return await prepareImageUpload(file,{forceReencode:true,format:'image/jpeg',maxEdge:2200})}catch(e){throw Error(e instanceof Error&&'code'in e&&e.code==='limit'?'largeFile':'invalidFile')}
}
