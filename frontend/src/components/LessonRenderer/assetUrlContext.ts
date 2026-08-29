import { createContext, useContext } from 'react'

export const AssetUrlContext = createContext<Readonly<Record<string, string>>>({})

export function useAssetUrl(src: string): string {
  const urls = useContext(AssetUrlContext)
  return urls[src] ?? src
}
