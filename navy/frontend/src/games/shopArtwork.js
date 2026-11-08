const images = {
  'task-reset': '/naval/shop/task-reset.png',
  'score-100': '/naval/shop/score-100.png',
  'bubble-captain': '/naval/shop/bubble-captain.png',
  'bubble-pirate': '/naval/shop/bubble-pirate.png',
  'bubble-tabby': '/naval/shop/bubble-tabby.png'
}

export const isBubbleItem = id => ['bubble-captain', 'bubble-pirate', 'bubble-tabby'].includes(id)
export const shopArtwork = id => ({ backgroundImage: images[id] ? `url("${images[id]}")` : 'none' })
