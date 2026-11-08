import { backendAssetUrl } from '../config/endpoints'

export function imageUrl(path) {
  return backendAssetUrl(path)
}
