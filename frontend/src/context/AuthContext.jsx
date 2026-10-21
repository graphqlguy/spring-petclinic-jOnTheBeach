import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { tokenStore } from '../graphql/client'

const AuthContext = createContext(null)

// Reads the user's name and scopes from the access token. The frontend only uses them
// to decide what to show; the backend checks the token on every request.
function readToken(token) {
  if (!token) {
    return null
  }
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(atob(part.padEnd(part.length + ((4 - (part.length % 4)) % 4), '=')))
    const scopes = Array.isArray(payload.scope) ? payload.scope : String(payload.scope ?? '').split(' ').filter(Boolean)
    return { name: payload.sub, scopes }
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => tokenStore.get())

  useEffect(() => tokenStore.listen(setToken), [])

  const value = useMemo(() => {
    const user = readToken(token)
    return {
      user,
      isStaff: user?.scopes.includes('petclinic:write') ?? false,
      signIn: (newToken) => tokenStore.set(newToken),
      signOut: () => tokenStore.set(null),
    }
  }, [token])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- the hook lives with its provider
export const useAuth = () => useContext(AuthContext)
