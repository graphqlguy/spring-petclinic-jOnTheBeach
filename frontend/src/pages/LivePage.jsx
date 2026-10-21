import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Errors, FeaturePending, PageTitle } from '../components/common'
import { personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useSubscription } from '../graphql/hooks'

export default function LivePage() {
  const { available } = useGraphQL()
  const [visits, setVisits] = useState([])
  const { error } = useSubscription('visitBooked', (data) => setVisits((previous) => [data.visitBooked, ...previous]), {
    skip: !available('visitBooked'),
  })

  if (!available('visitBooked')) {
    return <FeaturePending featureKey="live" />
  }
  return (
    <div>
      <PageTitle>Live bookings</PageTitle>
      <p className="mb-6 text-sm text-spring-grey">
        Listening for new visits. Book one in another tab, or ask the assistant to book one.
      </p>
      <Errors errors={error} />
      <ul className="space-y-3">
        {visits.map((visit) => (
          <li key={visit.id} className="rounded border border-spring-green/40 bg-spring-green/10 px-4 py-3 text-sm">
            <span className="mr-3">{visit.date}</span>
            <Link to={`/owners/${visit.pet.owner.id}`} className="text-spring-dark-green hover:underline">
              {visit.pet.name} of {personName(visit.pet.owner)}
            </Link>
            <span className="ml-3">{visit.description}</span>
            {visit.vet && <span className="ml-3 text-spring-grey">with {personName(visit.vet)}</span>}
          </li>
        ))}
      </ul>
    </div>
  )
}
