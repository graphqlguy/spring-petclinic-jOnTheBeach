import { Link } from 'react-router-dom'
import { Errors, FeaturePending, Loading, PageTitle, Table } from '../components/common'
import { personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useQuery } from '../graphql/hooks'

export default function VetsPage() {
  const { available } = useGraphQL()
  const { data, errors, loading } = useQuery('listVets', {}, { skip: !available('listVets') })
  const linked = available('vetSummary') || available('getVetDetails')

  if (!available('listVets')) {
    return <FeaturePending featureKey="vets" />
  }
  return (
    <div>
      <PageTitle>Veterinarians</PageTitle>
      <Errors errors={errors} />
      {loading && <Loading />}
      {data && (
        <Table headers={['Name', 'Specialties']}>
          {data.vets.map((vet) => (
            <tr key={vet.id}>
              <td className="px-3 py-2">
                {linked ? (
                  <Link to={`/vets/${vet.id}`} className="text-spring-dark-green hover:underline">
                    {personName(vet)}
                  </Link>
                ) : (
                  personName(vet)
                )}
              </td>
              <td className="px-3 py-2">
                {vet.specialties.length === 0 ? 'none' : vet.specialties.map((s) => s.name).join(', ')}
              </td>
            </tr>
          ))}
        </Table>
      )}
    </div>
  )
}
