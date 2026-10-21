import { buildClientSchema, Kind, validate } from 'graphql'
import { requestBody } from './client'

// Reads the backend's schema and validates every operation against it. An operation
// that validates is one the backend can answer now, so the page that uses it lights up.
//
// Result statuses:
//   'ready'        - the schema was read, `available` says which operations validate
//   'no-graphql'   - the backend answers, without a /graphql endpoint yet (before chapter 1)
//   'backend-down' - nothing answered on the backend port
export async function checkSchema(operations) {
  let response
  try {
    response = await fetch('/graphql', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/graphql-response+json, application/json' },
      body: JSON.stringify(requestBody(operations.schemaCheck)),
    })
  } catch {
    return { status: 'backend-down', available: {} }
  }
  if (response.status === 404 || response.status === 405) {
    return { status: 'no-graphql', available: {} }
  }
  let json
  try {
    json = await response.json()
  } catch {
    return { status: response.ok ? 'no-graphql' : 'backend-down', available: {} }
  }
  if (!json?.data?.__schema) {
    return { status: 'no-graphql', available: {}, errors: json?.errors }
  }
  const schema = buildClientSchema(json.data)
  const available = {}
  for (const operation of Object.values(operations)) {
    available[operation.name] = hasRootType(schema, operation.document) && validate(schema, operation.document).length === 0
  }
  return { status: 'ready', available }
}

// validate() accepts a mutation against a schema without a Mutation type, and leaves
// the refusal to execution, so the root type is checked here.
function hasRootType(schema, document) {
  return document.definitions
    .filter((definition) => definition.kind === Kind.OPERATION_DEFINITION)
    .every((definition) => schema.getRootType(definition.operation) != null)
}

// The chat endpoint from chapter 12 takes POST alone, so a GET gets 405 once it exists
// and 404 before.
export async function checkChat() {
  try {
    const response = await fetch('/api/chat')
    return response.status === 405
  } catch {
    return false
  }
}
