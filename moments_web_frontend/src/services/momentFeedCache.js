const memoryCaches = new Map()
const feedTypes = ['RECOMMENDED', 'HOT', 'RELATED']

function createTabState(type) {
  return {
    records: [],
    page: 1,
    hasMore: type !== 'HOT',
    newCount: 0,
    loaded: false,
    request: null,
    loadMoreRequest: null,
    loadMoreError: '',
    scrollTop: 0,
    anchorMomentId: null,
    anchorViewportOffset: null,
    returnMomentId: null
  }
}

export function getMomentFeedCache(scope = 'guest') {
  if (memoryCaches.has(scope)) return memoryCaches.get(scope)
  const tabs = Object.fromEntries(feedTypes.map(type => [type, createTabState(type)]))
  memoryCaches.set(scope, tabs)
  return tabs
}
