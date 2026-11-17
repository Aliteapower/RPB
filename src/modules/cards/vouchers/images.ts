import {paintVoucherTexture} from './texture'
import {voucherBenefit, voucherValue, voucherRedemptionTime, type Face, type VoucherPresentation} from './api'

export async function voucherShareImage(url: string, batchCode: string) {
  const {default:QRCode} = await import('qrcode')
  const qr = await QRCode.toCanvas(url, {width:320, margin:4})
  const image = document.createElement('canvas')
  image.width = qr.width
  image.height = qr.height + 46
  const ctx = image.getContext('2d')!
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, image.width, image.height)
  ctx.drawImage(qr, 0, 0)
  ctx.fillStyle = '#173d58'
  ctx.font = '600 21px sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText('幸运号 ' + batchCode, image.width / 2, qr.height + 13)
  return image.toDataURL('image/png')
}

export async function cropVoucherImage(file: File) {
  const image = await createImageBitmap(file)
  try {
    const sourceWidth = Math.min(image.width, image.height * 4 / 3)
    const sourceHeight = sourceWidth * 3 / 4
    const canvas = document.createElement('canvas')
    canvas.width = 1200
    canvas.height = 900
    const ctx = canvas.getContext('2d')!
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(image, (image.width - sourceWidth) / 2, (image.height - sourceHeight) / 2,
      sourceWidth, sourceHeight, 0, 0, canvas.width, canvas.height)
    for (const quality of [0.9, 0.78, 0.6, 0.4, 0.2]) {
      const result = canvas.toDataURL('image/jpeg', quality)
      if (result.length <= 1_333_355) return result
    }
    throw new Error('券图压缩失败，请换一张图片')
  } finally {
    image.close()
  }
}


function loadImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('券图生成失败'))
    image.src = source
  })
}

function wrap(ctx: CanvasRenderingContext2D, value: string, width: number) {
  const lines: string[] = []
  for (const paragraph of value.split(/\r?\n/)) {
    let line = ''
    for (const character of paragraph) {
      if (line && ctx.measureText(line + character).width > width) {
        lines.push(line)
        line = ''
      }
      line += character
    }
    lines.push(line)
  }
  return lines
}

export async function renderVoucherImage(face: Face, qr: string, brand?: VoucherPresentation, poster = false) {
  const [picture, barcode, logo] = await Promise.all([
    face.image ? loadImage(face.image) : null,
    loadImage(qr),
    brand?.logoUrl ? loadImage(brand.logoUrl).catch(() => null) : null
  ])
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')!
  const width = 800
  const inset = 40
  const inner = width - inset * 2
  function layout(value: string, size: number, weight = 400) {
    const font = `${weight} ${size}px sans-serif`
    ctx.font = font
    const lines = wrap(ctx, value, inner)
    const lineHeight = Math.ceil(size * 1.4)
    return {font, lines, lineHeight, height:lines.length * lineHeight}
  }

  const hero = [
    layout(face.kind === 'cash' ? '代金券' : '产品券', 26, 600),
    layout(face.kind === 'cash' ? voucherValue(face) : voucherBenefit(face), 62, 750),
    ...(face.kind === 'product' ? [layout('价值 ' + voucherValue(face), 26)] : []),
    layout(face.name, 30, 600)
  ]
  const details = [
    ...(brand ? [['幸运号',brand.batchCode]] : []),
    ...(poster && brand ? [['领取时间',voucherRedemptionTime(brand.startsAt,face.timezone)+' 至 '+voucherRedemptionTime(brand.endsAt,face.timezone)]] : []),
    ['使用日期', face.validFrom + ' 至 ' + face.validThrough],
    ['适用门店', face.stores.map(store => store.name).join('、')],
    ...(brand?.contacts ?? []).filter(store=>store.address||store.phone).map(store => [
      store.name,
      [store.address ? '地址 '+store.address : '', store.phone ? '电话 '+store.phone : ''].filter(Boolean).join('\n')
    ]),
    ['商品规格', face.specification],
    ['活动规则', face.rules]
  ].filter((field): field is [string, string] => !!field[1])
    .map(([label, value]) => ({label:layout(label, 22, 600), value:layout(value, 26)}))
  const heroHeight = inset * 2 + (logo ? 62 : 0) + hero.reduce((sum, row) => sum + row.height + 6, 0)
  const qrSectionHeight = 432
  const detailsHeight = details.reduce((sum, field) => sum + field.label.height + field.value.height + 18, 0)
  canvas.width = width
  canvas.height = heroHeight + qrSectionHeight + detailsHeight + inset * 2 + (picture ? 560 : 0)

  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, width, canvas.height)
  paintVoucherTexture(ctx, width, heroHeight)
  if (logo) {
    const box = {x:width-inset-116,y:inset,w:116,h:66}
    ctx.fillStyle = '#fff'
    ctx.fillRect(box.x,box.y,box.w,box.h)
    const scale = Math.min((box.w-12)/logo.naturalWidth,(box.h-12)/logo.naturalHeight)
    const w = logo.naturalWidth*scale, h = logo.naturalHeight*scale
    ctx.drawImage(logo,box.x+(box.w-w)/2,box.y+(box.h-h)/2,w,h)
  }
  ctx.textBaseline = 'top'
  function print(group: ReturnType<typeof layout>, y: number, color: string) {
    ctx.font = group.font
    ctx.fillStyle = color
    group.lines.forEach((line, index) => ctx.fillText(line, inset, y + index * group.lineHeight))
    return y + group.height
  }
  let y = inset + (logo ? 62 : 0)
  for (const row of hero) y = print(row, y, '#ffffff') + 6

  ctx.textAlign = 'center'
  ctx.font = '700 34px sans-serif'
  ctx.fillStyle = '#173d58'
  ctx.fillText(poster?'扫码抢券':'核销二维码', width / 2, heroHeight + 28)
  ctx.font = '24px sans-serif'
  ctx.fillStyle = '#4d6272'
  ctx.fillText(poster?'打开活动，领取你的专属券':'到店后出示此码', width / 2, heroHeight + 75)
  ctx.imageSmoothingEnabled = false
  ctx.drawImage(barcode, (width - 320) / 2, heroHeight + 108, 320, 320)
  ctx.imageSmoothingEnabled = true
  ctx.textAlign = 'start'
  y = heroHeight + qrSectionHeight
  ctx.strokeStyle = '#bacbd8'
  ctx.setLineDash([7, 6])
  ctx.beginPath()
  ctx.moveTo(inset, y)
  ctx.lineTo(width - inset, y)
  ctx.stroke()
  y += inset
  for (const field of details) {
    y = print(field.label, y, '#506575')
    y = print(field.value, y, '#172b36') + 18
  }
  if (picture) ctx.drawImage(picture, inset, y + 12, inner, inner * 3 / 4)
  return canvas.toDataURL('image/png')
}
