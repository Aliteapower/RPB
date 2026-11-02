"""Build the shipped Chinese PDF from the same content JSON as the web guide.
Usage: python scripts/docs/build_wine_storage_guide.py --font <Chinese TrueType/TTC>
Requires reportlab; rebuilding is a documentation operation, not part of runtime.
"""
import argparse,hashlib,json,os
from pathlib import Path
from xml.sax.saxutils import escape
from reportlab import rl_config
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import SimpleDocTemplate,Paragraph,Spacer,PageBreak,KeepTogether,Table,TableStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'src/modules/wine-storage/guide'
def build(font):
 rl_config.invariant=1
 pdfmetrics.registerFont(TTFont('GuideCJK',str(font),subfontIndex=0))
 content=(BASE/'content.json').read_bytes();d=json.loads(content);out=BASE/'guide-zh.pdf'
 green=colors.HexColor('#155F52');muted=colors.HexColor('#567277');ink=colors.HexColor('#16313B')
 style=lambda name,size=9.5,leading=15,**kw:ParagraphStyle(name,**dict(fontName='GuideCJK',fontSize=size,leading=leading,wordWrap='CJK',**{'textColor':ink}|kw))
 title=style('title',22,30,textColor=green);sub=style('sub',10,16,textColor=muted);body=style('body');task=style('task',13,19,textColor=green,spaceBefore=10,spaceAfter=3);hint=style('hint',9,14,textColor=muted,spaceAfter=6);step=style('step',9.5,15,spaceAfter=6);tip=style('tip',9,14,spaceAfter=5)
 def P(text,sty):return Paragraph(escape(text),sty)
 def footer(c,doc):
  c.setStrokeColor(colors.HexColor('#D9E4E6'));c.line(42,36,A4[0]-42,36);c.setFont('GuideCJK',8);c.setFillColor(muted);c.drawString(42,23,'操作指南 · '+d['version']);c.drawRightString(A4[0]-42,23,str(doc.page)+' / 2')
 story=[P(d['title']['zh'],title),P(d['intro']['zh'],sub),Spacer(1,9)]
 banner=Table([[P(d['keyRule']['zh'],body)]],colWidths=[A4[0]-84]);banner.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,-1),colors.HexColor('#EAF4EF')),('BOX',(0,0),(-1,-1),0.5,colors.HexColor('#CFDFD7')),('TOPPADDING',(0,0),(-1,-1),9),('BOTTOMPADDING',(0,0),(-1,-1),9),('LEFTPADDING',(0,0),(-1,-1),10),('RIGHTPADDING',(0,0),(-1,-1),10)]));story+=[banner,Spacer(1,3)]
 for index,s in enumerate(d['sections']):
  if index==3:story.extend([PageBreak(),P('核销、查询与开店准备',title),P('先分清任务，再按步骤完成。',sub),Spacer(1,6)])
  story.append(KeepTogether([P(str(index+1).zfill(2)+'  '+s['title']['zh'],task),P(s['hint']['zh'],hint)]))
  for i,st in enumerate(s['steps'],1):story.append(P(str(i)+'. '+st['title']['zh']+'  /  '+st['text']['zh'],step))
 if d['tips']:
  story.append(P('遇到问题，先这样处理',task))
  for t in d['tips']:story.append(P(t['title']['zh']+'：'+t['text']['zh'],tip))
 doc=SimpleDocTemplate(str(out),pagesize=A4,rightMargin=42,leftMargin=42,topMargin=32,bottomMargin=46,title=d['title']['zh'],author='RPB',subject='商户与员工酒水寄存分步指南')
 doc.build(story,onFirstPage=footer,onLaterPages=footer)
 import fitz
 with fitz.open(out) as pdf:
  if len(pdf)!=2:raise RuntimeError('Expected a concise two-page guide, got '+str(len(pdf)))
 manifest={'version':d['version'],'contentSha256':hashlib.sha256(content).hexdigest(),'pdfSha256':hashlib.sha256(out.read_bytes()).hexdigest(),'pages':2,'language':'zh-CN'}
 (BASE/'guide-zh.manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
 print(json.dumps(manifest))
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--font',default=os.environ.get('RPB_GUIDE_FONT','C:/Windows/Fonts/msyh.ttc'));args=parser.parse_args();build(args.font)
