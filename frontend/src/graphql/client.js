import { createClient } from 'graphql-ws'

const TOKEN_KEY = 'petclinic.token'
const listeners = new Set()

// The access token from the login mutation, kept across page reloads.
export const tokenStore = {
  get() {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token) {
    try {
      if (token) {
        localStorage.setItem(TOKEN_KEY, token)
      } else {
        localStorage.removeItem(TOKEN_KEY)
      }
    } catch {
      // storage can be blocked; the token then lasts until the page reloads
    }
    listeners.forEach((listener) => listener(token))
  },
  listen(listener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}

export function requestBody(operation, variables = {}) {
  return {
    query: operation.query,
    operationName: operation.operationName,
    variables,
    // Servers before chapter 11 ignore this; from then on it names a trusted document
    extensions: operation.hash ? { persistedQuery: { version: 1, sha256Hash: operation.hash } } : undefined,
  }
}

async function post(body, token) {
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/graphql-response+json, application/json',
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return fetch('/graphql', { method: 'POST', headers, body: JSON.stringify(body) })
}

// Sends one operation and returns { data, errors }, as a GraphQL response does.
export async function execute(operation, variables = {}) {
  const body = requestBody(operation, variables)
  let token = tokenStore.get()
  let response
  try {
    response = await post(body, token)
    if (response.status === 401 && token) {
      // the token expired, or was signed before a key change: sign out and try anonymously
      tokenStore.set(null)
      token = null
      response = await post(body, token)
    }
  } catch (error) {
    return { errors: [{ message: `The backend did not answer: ${error.message}` }] }
  }
  const text = await response.text()
  try {
    return JSON.parse(text)
  } catch {
    return { errors: [{ message: `The backend answered HTTP ${response.status} without a GraphQL response` }] }
  }
}

let webSocketClient

function webSocket() {
  if (!webSocketClient) {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
    webSocketClient = createClient({
      url: `${scheme}://${location.host}/graphql`,
      connectionParams: () => {
        const token = tokenStore.get()
        return token ? { Authorization: `Bearer ${token}` } : {}
      },
      retryAttempts: 3,
    })
  }
  return webSocketClient
}

// Runs a subscription over a WebSocket and returns the function that stops it.
export function subscribe(operation, variables, sink) {
  return webSocket().subscribe(requestBody(operation, variables), sink)
}
