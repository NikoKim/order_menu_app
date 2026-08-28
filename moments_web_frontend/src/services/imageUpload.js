function normalizedQuarterTurns(value) {
  return ((Number(value || 0) % 4) + 4) % 4
}

async function decodeUploadImage(file) {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' })
    } catch { }
  }

  const url = URL.createObjectURL(file)
  try {
    const image = new Image()
    image.decoding = 'async'
    image.src = url
    await image.decode()
    return image
  } finally {
    URL.revokeObjectURL(url)
  }
}

function canvasBlob(canvas, type, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(blob => {
      if (blob) resolve(blob)
      else reject(new Error('图片处理失败，请重新选择'))
    }, type, quality)
  })
}

export async function prepareUploadImage(file, quarterTurns = 0) {
  const source = await decodeUploadImage(file)
  try {
    const turns = normalizedQuarterTurns(quarterTurns)
    const sourceWidth = source.naturalWidth || source.width
    const sourceHeight = source.naturalHeight || source.height
    if (!sourceWidth || !sourceHeight) throw new Error('无法读取图片尺寸')
    const maxPixels = 16_000_000
    const scale = Math.min(1, Math.sqrt(maxPixels / (sourceWidth * sourceHeight)))
    const drawWidth = Math.max(1, Math.round(sourceWidth * scale))
    const drawHeight = Math.max(1, Math.round(sourceHeight * scale))

    const swapSides = turns % 2 === 1
    const canvas = document.createElement('canvas')
    canvas.width = swapSides ? drawHeight : drawWidth
    canvas.height = swapSides ? drawWidth : drawHeight
    const context = canvas.getContext('2d', { alpha: false })
    if (!context) throw new Error('当前浏览器无法处理图片')

    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.translate(canvas.width / 2, canvas.height / 2)
    context.rotate(turns * Math.PI / 2)
    context.drawImage(source, -drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight)

    const blob = await canvasBlob(canvas, 'image/jpeg', 0.94)
    const originalName = file.name || 'moment-image'
    const baseName = originalName.replace(/\.[^.]+$/, '') || 'moment-image'
    return new File([blob], `${baseName}.jpg`, {
      type: 'image/jpeg',
      lastModified: Date.now()
    })
  } finally {
    source.close?.()
  }
}
