import { Link, useSearchParams } from 'react-router-dom'
import { Errors, FeaturePending, Loading, PageTitle } from '../components/common'
import { personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useQuery } from '../graphql/hooks'

// One search returns owners, vets and pets together; __typename says which is which
function Result({ item }) {
  switch (item.__typename) {
    case 'Owner':
      return (
        <Link to={`/owners/${item.id}`} className="text-spring-dark-green hover:underline">
          {personName(item)} <span className="text-spring-grey">· owner in {item.city}</span>
        </Link>
      )
    case 'Vet':
      return (
        <Link to={`/vets/${item.id}`} className="text-spring-dark-green hover:underline">
          {personName(item)} <span className="text-spring-grey">· vet</span>
        </Link>
      )
    case 'Pet':
      return (
        <Link to={`/owners/${item.owner.id}`} className="text-spring-dark-green hover:underline">
          {item.name}{' '}
          <span className="text-spring-grey">
            · {item.type.name} of {personName(item.owner)}
          </span>
        </Link>
      )
    default:
      return <span>{item.__typename}</span>
  }
}

export default function SearchPage() {
  const { available } = useGraphQL()
  const [params] = useSearchParams()
  const text = params.get('q') ?? ''
  const { data, errors, loading } = useQuery('search', { text }, { skip: !available('search') || !text })

  if (!available('search')) {
    return <FeaturePending featureKey="search" />
  }
  return (
    <div>
      <PageTitle>Search for “{text}”</PageTitle>
      <Errors errors={errors} />
      {loading && <Loading />}
      {data &&
        (data.search.length === 0 ? (
          <p className="text-spring-grey">Nothing matches.</p>
        ) : (
          <ul className="space-y-2">
            {data.search.map((item) => (
              <li key={`${item.__typename}-${item.id}`}>
                <Result item={item} />
              </li>
            ))}
          </ul>
        ))}
    </div>
  )
}
