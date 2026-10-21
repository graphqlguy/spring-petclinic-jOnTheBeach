import { Link } from 'react-router-dom'
import { feature } from '../features'

// Shown in place of a page, or a part of one, whose operations the backend cannot
// answer yet.
export function FeaturePending({ featureKey, compact = false }) {
  const f = feature(featureKey)
  if (compact) {
    return (
      <p className="rounded border border-dashed border-spring-grey px-4 py-3 text-sm text-spring-grey">
        {f.title} appear here after chapter {f.chapter}, which adds {f.needs}.
      </p>
    )
  }
  return (
    <div className="mx-auto max-w-xl py-16 text-center">
      <p className="mb-4 text-4xl">🚧</p>
      <h1 className="mb-3 text-2xl">{f.title}</h1>
      <p className="text-spring-grey">
        This page lights up in chapter {f.chapter}, which adds {f.needs}. Build it, restart the backend, and{' '}
        <Link to="/" className="text-spring-dark-green underline">
          check again
        </Link>
        .
      </p>
    </div>
  )
}

// The errors of a GraphQL response: the message, and the classification and the field
// when the server sends them.
export function Errors({ errors }) {
  if (!errors?.length) {
    return null
  }
  return (
    <ul className="my-4 space-y-1 rounded border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">
      {errors.map((error, i) => (
        <li key={i}>
          {error.extensions?.classification && (
            <span className="mr-2 rounded bg-red-200 px-1.5 py-0.5 font-mono text-xs">
              {error.extensions.classification}
            </span>
          )}
          {error.extensions?.field && <span className="mr-1 font-mono">{error.extensions.field}:</span>}
          {error.message}
          {error.path && <span className="ml-2 font-mono text-xs text-red-600">at {error.path.join('.')}</span>}
        </li>
      ))}
    </ul>
  )
}

export function Loading() {
  return <p className="py-8 text-spring-grey">Loading…</p>
}

export function PageTitle({ children, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl">{children}</h1>
      {actions && <div className="flex gap-2">{actions}</div>}
    </div>
  )
}

export function ButtonLink({ to, children }) {
  return (
    <Link to={to} className="rounded bg-spring-green px-4 py-2 text-sm text-white hover:bg-spring-dark-green">
      {children}
    </Link>
  )
}

export function Button({ children, ...props }) {
  return (
    <button
      {...props}
      className="rounded bg-spring-green px-4 py-2 text-sm text-white hover:bg-spring-dark-green disabled:opacity-50"
    >
      {children}
    </button>
  )
}

export function Table({ headers, children }) {
  return (
    <table className="w-full border-collapse text-left text-sm">
      <thead>
        <tr className="bg-spring-brown text-spring-light-grey">
          {headers.map((header) => (
            <th key={header} className="px-3 py-2 font-normal">
              {header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody className="[&>tr:nth-child(even)]:bg-spring-light-grey">{children}</tbody>
    </table>
  )
}

export function Field({ label, error, children }) {
  return (
    <label className="mb-4 block">
      <span className="mb-1 block text-sm text-spring-grey">{label}</span>
      {children}
      {error && <span className="mt-1 block text-sm text-red-700">{error}</span>}
    </label>
  )
}
