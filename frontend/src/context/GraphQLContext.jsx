import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { loadOperations } from '../graphql/operations'
import { checkChat, checkSchema } from '../graphql/schema'

const GraphQLContext = createContext(null)

// Loads the operation files once, then checks them against the backend's schema.
// `available(...names)` says whether the backend answers all of those operations now;
// `reload` checks again, after you change the schema and restart the backend.
export function GraphQLProvider({ children }) {
  const [operations, setOperations] = useState(null)
  const [check, setCheck] = useState({ status: 'loading', available: {}, chat: false })

  const runCheck = useCallback(async (ops) => {
    const [schema, chat] = await Promise.all([checkSchema(ops), checkChat()])
    setCheck({ ...schema, chat })
  }, [])

  useEffect(() => {
    let active = true
    loadOperations().then((ops) => {
      if (active) {
        setOperations(ops)
        runCheck(ops)
      }
    })
    return () => {
      active = false
    }
  }, [runCheck])

  const value = useMemo(
    () => ({
      status: operations ? check.status : 'loading',
      chat: check.chat,
      available: (...names) => names.every((name) => check.available[name] === true),
      operation: (name) => operations?.[name],
      reload: () => {
        if (operations) {
          setCheck((previous) => ({ ...previous, status: 'loading' }))
          runCheck(operations)
        }
      },
    }),
    [operations, check, runCheck],
  )

  return <GraphQLContext.Provider value={value}>{children}</GraphQLContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- the hook lives with its provider
export const useGraphQL = () => useContext(GraphQLContext)
