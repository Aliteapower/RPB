import {MultiFormatReader,BarcodeFormat,DecodeHintType,BinaryBitmap,HybridBinarizer,RGBLuminanceSource,NotFoundException,ChecksumException,FormatException} from '@zxing/library'
export function createBarcodeDecoder(){
 const canvas=document.createElement('canvas'),context=canvas.getContext('2d',{willReadFrequently:true}),reader=new MultiFormatReader()
 reader.setHints(new Map<DecodeHintType,unknown>([[DecodeHintType.POSSIBLE_FORMATS,[BarcodeFormat.EAN_8,BarcodeFormat.EAN_13,BarcodeFormat.UPC_A,BarcodeFormat.ITF]],[DecodeHintType.TRY_HARDER,true]]))
 function decode(source:CanvasImageSource,width:number,height:number){
  if(!context||!width||!height)return null
  const scale=Math.min(1,1280/Math.max(width,height));canvas.width=Math.round(width*scale);canvas.height=Math.round(height*scale);context.drawImage(source,0,0,canvas.width,canvas.height)
  const {data}=context.getImageData(0,0,canvas.width,canvas.height),grey=new Uint8ClampedArray(canvas.width*canvas.height)
  for(let i=0;i<grey.length;i++)grey[i]=(data[i*4]+2*data[i*4+1]+data[i*4+2])/4
  try{return reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(grey,canvas.width,canvas.height)))).getText()}
  catch(e){if(e instanceof NotFoundException||e instanceof ChecksumException||e instanceof FormatException)return null;throw e}
 }
 return {video:(v:HTMLVideoElement)=>decode(v,v.videoWidth,v.videoHeight),image:(i:HTMLImageElement)=>decode(i,i.naturalWidth,i.naturalHeight)}
}
