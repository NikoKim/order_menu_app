export function navalPublicPlayerId(person) {
  const value = person?.medals?.medalId
  return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value) ? value.toLowerCase() : ''
}

export function canOpenNavalPlayer(person) {
  return Boolean(person && !person.ai && (person.self || navalPublicPlayerId(person)))
}
