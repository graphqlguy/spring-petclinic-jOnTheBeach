import { Link } from 'react-router-dom'
import pets from '../assets/pets.png'
import { useGraphQL } from '../context/GraphQLContext'
import { FEATURES } from '../features'

const PATHS = { owners: '/owners', vets: '/vets', agenda: '/visits', login: '/login', live: '/live' }

function BackendStatus({ status }) {
  if (status === 'loading') {
    return <p className="text-spring-grey">Checking the backend…</p>
  }
  if (status === 'backend-down') {
    return (
      <p>
        The backend is not running. Start it from the project folder with <code>./mvnw spring-boot:run</code>, then
        check again.
      </p>
    )
  }
  if (status === 'no-graphql') {
    return <p>The backend runs, but it has no GraphQL endpoint yet. Chapter 1 adds it.</p>
  }
  return null
}

export default function HomePage() {
  const { status, available, chat, reload } = useGraphQL()
  return (
    <div>
      <h1 className="mb-6 text-2xl">Welcome</h1>
      <img src={pets} alt="Pets" className="mx-auto mb-8 max-w-sm" />
      <section className="rounded border border-spring-light-grey p-5">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg">What the GraphQL API can do so far</h2>
          <button onClick={reload} className="text-sm text-spring-dark-green underline">
            Check again
          </button>
        </div>
        <BackendStatus status={status} />
        <ul className="mt-3 space-y-1 text-sm">
          {FEATURES.map((f) => {
            const live = status === 'ready' && available(...f.operations)
            const path = PATHS[f.key]
            return (
              <li key={f.key} className="flex items-center gap-3">
                <span className="w-5 text-center">{live ? '✅' : '🚧'}</span>
                <span className="w-20 text-spring-grey">Chapter {f.chapter}</span>
                {live && path ? (
                  <Link to={path} className="text-spring-dark-green underline">
                    {f.title}
                  </Link>
                ) : (
                  <span className={live ? '' : 'text-spring-grey'}>{f.title}</span>
                )}
              </li>
            )
          })}
          <li className="flex items-center gap-3">
            <span className="w-5 text-center">{chat ? '✅' : '🚧'}</span>
            <span className="w-20 text-spring-grey">Chapter 12</span>
            <span className={chat ? '' : 'text-spring-grey'}>Clinic assistant</span>
          </li>
        </ul>
        <p className="mt-4 text-xs text-spring-grey">
          After you change the schema: restart the backend, then check again or reload this page.
        </p>
      </section>
    </div>
  )
}
