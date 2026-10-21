import { Kind, parse, print, visit } from 'graphql'

// Every operation the frontend sends lives as a .graphql file with the backend, in
// src/main/resources. The files in operations/ are the ones an agent can use as tools
// from chapter 11 on; the files in operations-ui/ serve the frontend alone.
const files = {
  ...import.meta.glob('../../../src/main/resources/operations/**/*.graphql', {
    query: '?raw',
    import: 'default',
    eager: true,
  }),
  ...import.meta.glob('../../../src/main/resources/operations-ui/**/*.graphql', {
    query: '?raw',
    import: 'default',
    eager: true,
  }),
}

// GATool reads @gatool(...) on an operation. The GraphQL API does not declare that
// directive, so it comes out before the frontend validates or sends a document.
function withoutGatoolDirective(document) {
  return visit(document, {
    Directive(node) {
      return node.name.value === 'gatool' ? null : undefined
    },
  })
}

// The SHA-256 of the file exactly as it is on disk. Chapter 11 has the server keep the
// same hashes, so that a request can name a trusted document by its hash.
async function sha256(text) {
  if (!globalThis.crypto?.subtle) {
    return null
  }
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

export async function loadOperations() {
  const operations = {}
  for (const [path, text] of Object.entries(files)) {
    const name = path.split('/').pop().replace(/\.graphql$/, '')
    const document = withoutGatoolDirective(parse(text))
    const definition = document.definitions.find((d) => d.kind === Kind.OPERATION_DEFINITION)
    operations[name] = {
      name,
      document,
      query: print(document),
      operationName: definition?.name?.value,
      hash: await sha256(text),
      file: path.slice(path.indexOf('src/main/resources/')),
    }
  }
  return operations
}
