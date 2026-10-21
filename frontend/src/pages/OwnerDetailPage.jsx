import { Link, useParams } from 'react-router-dom'
import { ButtonLink, Errors, FeaturePending, Loading, PageTitle, Table } from '../components/common'
import { personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useQuery } from '../graphql/hooks'

// The field-level errors go next to their field; the rest go in the list
function split(errors) {
  const hidden = new Set((errors ?? []).filter((e) => e.path?.length === 2).map((e) => e.path[1]))
  return { hidden, others: (errors ?? []).filter((e) => e.path?.length !== 2) }
}

function Pet({ ownerId, pet }) {
  const { available } = useGraphQL()
  return (
    <div className="mb-6 rounded border border-spring-light-grey p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <span className="text-lg">{pet.name}</span>
          <span className="ml-3 text-sm text-spring-grey">
            {pet.type?.name} · born {pet.birthDate ?? 'unknown'}
          </span>
        </div>
        <div className="flex gap-2">
          {available('updatePet', 'listPetTypes') && (
            <ButtonLink to={`/owners/${ownerId}/pets/${pet.id}/edit`}>Edit pet</ButtonLink>
          )}
          {available('addVisit', 'listVets') && (
            <ButtonLink to={`/owners/${ownerId}/pets/${pet.id}/visits/new`}>Book visit</ButtonLink>
          )}
        </div>
      </div>
      {pet.visits.length === 0 ? (
        <p className="text-sm text-spring-grey">No visits yet.</p>
      ) : (
        <Table headers={['Date', 'Description', 'Vet']}>
          {pet.visits.map((visit) => (
            <tr key={visit.id}>
              <td className="px-3 py-2 whitespace-nowrap">{visit.date}</td>
              <td className="px-3 py-2">{visit.description}</td>
              <td className="px-3 py-2">
                {visit.vet && (
                  <Link to={`/vets/${visit.vet.id}`} className="text-spring-dark-green hover:underline">
                    {personName(visit.vet)}
                  </Link>
                )}
              </td>
            </tr>
          ))}
        </Table>
      )}
    </div>
  )
}

export default function OwnerDetailPage() {
  const { ownerId } = useParams()
  const { available } = useGraphQL()
  const detailed = available('getOwnerDetails')
  const operation = detailed ? 'getOwnerDetails' : 'ownerSummary'
  const { data, errors, loading } = useQuery(operation, { id: ownerId }, { skip: !available(operation) })

  if (!available(operation)) {
    return <FeaturePending featureKey="ownerPage" />
  }
  if (loading) {
    return <Loading />
  }
  const owner = data?.owner
  const { hidden, others } = split(errors)
  if (!owner) {
    return (
      <div>
        <Errors errors={errors} />
        {!errors && <p>No owner has the id {ownerId}.</p>}
      </div>
    )
  }
  const value = (field) =>
    hidden.has(field) ? <span className="text-spring-grey">🔒 visible to staff</span> : owner[field]

  return (
    <div>
      <PageTitle
        actions={available('updateOwner') && <ButtonLink to={`/owners/${owner.id}/edit`}>Edit owner</ButtonLink>}
      >
        {personName(owner)}
      </PageTitle>
      <Errors errors={others} />
      <table className="mb-8 text-sm">
        <tbody>
          {[
            ['Address', value('address')],
            ['City', value('city')],
            ['Telephone', value('telephone')],
          ].map(([label, content]) => (
            <tr key={label}>
              <th className="py-1 pr-6 text-left font-normal text-spring-grey">{label}</th>
              <td className="py-1">{content}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg">Pets and visits</h2>
        {detailed && available('addPet', 'listPetTypes') && (
          <ButtonLink to={`/owners/${owner.id}/pets/new`}>Add pet</ButtonLink>
        )}
      </div>
      {detailed ? (
        owner.pets.length === 0 ? (
          <p className="text-sm text-spring-grey">No pets yet.</p>
        ) : (
          owner.pets.map((pet) => <Pet key={pet.id} ownerId={owner.id} pet={pet} />)
        )
      ) : (
        <FeaturePending featureKey="ownerDetails" compact />
      )}
    </div>
  )
}
