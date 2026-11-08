export const NAVAL_STICKERS = [
  ['salute', '出航致敬'], ['surprised', '被发现了'], ['heart', '收到心意'], ['surrender', '认输啦'],
  ['sleepy', '等得困了'], ['applause', '打得漂亮'], ['challenge', '放马过来'], ['reconnect', '信号掉线']
].map(([id, name]) => ({ id, name, src: `/naval/stickers/${id}.png` }))

export const stickerMessage = id => `::naval-sticker:${id}::`
export function messageSticker(text) {
  return NAVAL_STICKERS.find(sticker => stickerMessage(sticker.id) === text) || null
}
