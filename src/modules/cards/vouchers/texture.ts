/** Engraved guilloche lines shared by the live voucher and exported artwork. */
export function paintVoucherTexture(ctx: CanvasRenderingContext2D, width: number, height: number) {
  ctx.save()
  const scale=width/800
  const h=height/scale
  ctx.scale(scale,scale)
  ctx.beginPath()
  ctx.rect(0,0,800,h)
  ctx.clip()

  const base=ctx.createLinearGradient(0,h,800,0)
  base.addColorStop(0,'#102c49')
  base.addColorStop(0.45,'#174a64')
  base.addColorStop(0.78,'#247176')
  base.addColorStop(1,'#3b7588')
  ctx.fillStyle=base
  ctx.fillRect(0,0,800,h)
  const glow=ctx.createRadialGradient(610,h*0.08,0,610,h*0.08,580)
  glow.addColorStop(0,'rgba(157,216,207,0.16)')
  glow.addColorStop(1,'rgba(157,216,207,0)')
  ctx.fillStyle=glow
  ctx.fillRect(0,0,800,h)

  // Interleaved fine waves resemble an engraved banknote border.
  ctx.lineWidth=0.7
  ctx.strokeStyle='rgba(197,233,222,0.12)'
  for(let line=-h;line<800+h;line+=9) {
    ctx.beginPath()
    for(let y=0;y<=h;y+=3) {
      const x=line+y*0.32+12*Math.sin(y/33+line/95)
      if(y===0)ctx.moveTo(x,y)
      else ctx.lineTo(x,y)
    }
    ctx.stroke()
  }

  // Offset closed rosettes add depth without crossing the white QR area.
  const centerX=665, centerY=h*0.75
  ctx.lineWidth=0.8
  for(let ring=0;ring<26;ring++) {
    ctx.beginPath()
    const radius=38+ring*7
    for(let point=0;point<=720;point++) {
      const angle=point*Math.PI/360
      const modulation=8*Math.sin(angle*14+ring*0.16)+4*Math.cos(angle*7)
      const r=radius+modulation
      const x=centerX+r*Math.cos(angle)*1.3
      const y=centerY+r*Math.sin(angle)*0.72
      if(point===0)ctx.moveTo(x,y)
      else ctx.lineTo(x,y)
    }
    ctx.closePath()
    ctx.strokeStyle=ring%3===0?'rgba(222,220,166,0.2)':'rgba(205,238,227,0.13)'
    ctx.stroke()
  }

  ctx.strokeStyle='rgba(226,235,213,0.28)'
  ctx.lineWidth=1
  ctx.beginPath()
  ctx.roundRect(18,18,764,Math.max(0,h-36),16)
  ctx.stroke()
  ctx.strokeStyle='rgba(226,235,213,0.11)'
  ctx.beginPath()
  ctx.roundRect(24,24,752,Math.max(0,h-48),12)
  ctx.stroke()
  ctx.restore()
}
