import { useCallback, useEffect, useRef, useState } from 'react'
import { useGraphQL } from '../context/GraphQLContext'
import { execute, subscribe } from './client'

// Runs a query when the component shows, and again when the variables change.
export function useQuery(name, variables, { skip = false } = {}) {
  const { operation } = useGraphQL()
  const op = operation(name)
  const json = JSON.stringify(variables ?? {})
  const [version, setVersion] = useState(0)
  const [result, setResult] = useState({ key: null, data: null, errors: null })
  const key = skip || !op ? null : `${name}:${json}:${version}`

  useEffect(() => {
    if (!key) {
      return undefined
    }
    let active = true
    execute(op, JSON.parse(json)).then((response) => {
      if (active) {
        setResult({ key, data: response.data ?? null, errors: response.errors ?? null })
      }
    })
    return () => {
      active = false
    }
  }, [key, op, json])

  const current = result.key === key
  return {
    loading: key !== null && !current,
    data: current ? result.data : null,
    errors: current ? result.errors : null,
    refetch: () => setVersion((v) => v + 1),
  }
}

// Returns a function that sends the mutation, and the state of the last call.
export function useMutation(name) {
  const { operation } = useGraphQL()
  const [state, setState] = useState({ loading: false, data: null, errors: null })
  const mutate = useCallback(
    async (variables) => {
      setState({ loading: true, data: null, errors: null })
      const response = await execute(operation(name), variables)
      const next = { loading: false, data: response.data ?? null, errors: response.errors ?? null }
      setState(next)
      return next
    },
    [operation, name],
  )
  return [mutate, state]
}

// Calls onData with each event of a subscription while the component shows.
export function useSubscription(name, onData, { skip = false } = {}) {
  const { operation } = useGraphQL()
  const op = operation(name)
  const handler = useRef(onData)
  const [error, setError] = useState(null)

  useEffect(() => {
    handler.current = onData
  })

  useEffect(() => {
    if (skip || !op) {
      return undefined
    }
    return subscribe(op, {}, {
      next: (event) => (event.data ? handler.current(event.data) : setError(event.errors)),
      error: (problem) => setError([{ message: problem?.reason || problem?.message || 'The subscription failed' }]),
      complete: () => {},
    })
  }, [op, skip])

  return { error }
}
