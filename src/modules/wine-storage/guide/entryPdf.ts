import {PDFDocument,StandardFonts,rgb,PDFName,PDFString} from 'pdf-lib'
import type {GuideEntries} from './entries'
/** Append store-specific distribution codes without changing the reviewed two-page manual. */
export async function appendGuideEntries(base:Uint8Array,entries:GuideEntries,images:{staff:string;customer:string},template:Uint8Array){
 const pdf=await PDFDocument.load(base),background=await PDFDocument.load(template),[sheet]=await pdf.copyPages(background,[0]),page=pdf.addPage(sheet),font=await pdf.embedFont(StandardFonts.Helvetica)
 pdf.setTitle('酒水寄存操作指南 · '+entries.storeName)
 for(const [index,key] of (['staff','customer'] as const).entries()){
  const top=index===0?743:393,image=await pdf.embedPng(images[key]),scaled=image.scaleToFit(210,295),url=entries[key]
  page.drawImage(image,{x:38,y:top-scaled.height,width:scaled.width,height:scaled.height})
  const chunks=url.match(/.{1,43}/g)??[]
  chunks.forEach((line,i)=>page.drawText(line,{x:267,y:top-62-i*13,size:9,font}))
  const annotation=pdf.context.register(pdf.context.obj({Type:'Annot',Subtype:'Link',Rect:[263,top-70-chunks.length*13,558,top-42],Border:[0,0,0],A:{S:'URI',URI:PDFString.of(url)}}))
  page.node.addAnnot(annotation)
 }
 for(const [index,p] of pdf.getPages().entries()){p.drawRectangle({x:510,y:15,width:65,height:20,color:rgb(1,1,1)});p.drawText(`${index+1} / 3`,{x:535,y:index===2?28:23,size:8,font})}
 return pdf.save()
}
