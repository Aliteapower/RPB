import {voucherBenefit, voucherValue, type Face} from './api'

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

export async function downloadVoucher(face: Face, qr: string) {
  const [picture, barcode] = await Promise.all([face.image ? loadImage(face.image) : null, loadImage(qr)])
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
    ['使用日期', face.validFrom + ' 至 ' + face.validThrough],
    ['适用门店', face.stores.map(store => store.name).join('、')],
    ['商品规格', face.specification],
    ['活动规则', face.rules]
  ].filter((field): field is [string, string] => !!field[1])
    .map(([label, value]) => ({label:layout(label, 22, 600), value:layout(value, 26)}))
  const heroHeight = inset * 2 + hero.reduce((sum, row) => sum + row.height + 6, 0)
  const qrSectionHeight = 432
  const detailsHeight = details.reduce((sum, field) => sum + field.label.height + field.value.height + 18, 0)
  canvas.width = width
  canvas.height = heroHeight + qrSectionHeight + detailsHeight + inset * 2 + (picture ? 560 : 0)

  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, width, canvas.height)
  ctx.fillStyle = '#173d58'
  ctx.fillRect(0, 0, width, heroHeight)
  ctx.textBaseline = 'top'
  function print(group: ReturnType<typeof layout>, y: number, color: string) {
    ctx.font = group.font
    ctx.fillStyle = color
    group.lines.forEach((line, index) => ctx.fillText(line, inset, y + index * group.lineHeight))
    return y + group.height
  }
  let y = inset
  for (const row of hero) y = print(row, y, '#ffffff') + 6

  ctx.textAlign = 'center'
  ctx.font = '700 34px sans-serif'
  ctx.fillStyle = '#173d58'
  ctx.fillText('核销二维码', width / 2, heroHeight + 28)
  ctx.font = '24px sans-serif'
  ctx.fillStyle = '#4d6272'
  ctx.fillText('到店后出示此码', width / 2, heroHeight + 75)
  ctx.imageSmoothingEnabled = false
  ctx.drawImage(barcode, (width - 320) / 2, heroHeight + 108)
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
  const link = document.createElement('a')
  link.href = canvas.toDataURL('image/png')
  link.download = face.name + '.png'
  link.click()
}
