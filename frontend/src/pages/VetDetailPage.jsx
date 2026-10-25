import { Link, useParams } from 'react-router-dom'
import { Errors, FeaturePending, Loading, PageTitle, Table } from '../components/common'
import { personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useQuery } from '../graphql/hooks'

export default function VetDetailPage() {
  const { vetId } = useParams()
  const { available } = useGraphQL()
  // The page grows with the schema: the vet, then their visits, then each visit's pet and owner
  const detailed = available('getVetDetails')
  const withVisits = detailed || available('vetVisits')
  const operation = detailed ? 'getVetDetails' : withVisits ? 'vetVisits' : 'vetSummary'
  const { data, errors, loading } = useQuery(operation, { id: vetId }, { skip: !available(operation) })

  if (!available(operation)) {
    return <FeaturePending featureKey="vetPage" />
  }
  if (loading) {
    return <Loading />
  }
  const vet = data?.vet
  if (!vet) {
    return <Errors errors={errors ?? [{ message: `No vet has the id ${vetId}.` }]} />
  }
  return (
    <div>
      <PageTitle>{personName(vet)}</PageTitle>
      <Errors errors={errors} />
      <p className="mb-8 text-sm">
        <span className="mr-3 text-spring-grey">Specialties</span>
        {vet.specialties.length === 0 ? 'none' : vet.specialties.map((s) => s.name).join(', ')}
      </p>
      <h2 className="mb-4 text-lg">Visits</h2>
      {withVisits ? (
        <>
          {vet.visits.length === 0 ? (
            <p className="text-sm text-spring-grey">No visits yet.</p>
          ) : (
            <Table headers={detailed ? ['Date', 'Pet', 'Owner', 'Description'] : ['Date', 'Description']}>
              {vet.visits.map((visit) => (
                <tr key={visit.id}>
                  <td className="px-3 py-2 whitespace-nowrap">{visit.date}</td>
                  {detailed && (
                    <>
                      <td className="px-3 py-2">
                        {visit.pet.name} <span className="text-spring-grey">({visit.pet.type.name})</span>
                      </td>
                      <td className="px-3 py-2">
                        <Link to={`/owners/${visit.pet.owner.id}`} className="text-spring-dark-green hover:underline">
                          {personName(visit.pet.owner)}
                        </Link>
                      </td>
                    </>
                  )}
                  <td className="px-3 py-2">{visit.description}</td>
                </tr>
              ))}
            </Table>
          )}
          {!detailed && (
            <div className="mt-4">
              <FeaturePending featureKey="vetDetails" compact />
            </div>
          )}
        </>
      ) : (
        <FeaturePending featureKey="vetVisits" compact />
      )}
    </div>
  )
}
