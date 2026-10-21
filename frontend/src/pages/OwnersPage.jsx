import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ButtonLink, Errors, FeaturePending, Loading, PageTitle, Table } from '../components/common'
import { inputClass, personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useQuery } from '../graphql/hooks'

const SIZE = 10

export default function OwnersPage() {
  const { available } = useGraphQL()
  const [params, setParams] = useSearchParams()
  const filter = { lastName: params.get('lastName') ?? '', city: params.get('city') ?? '' }
  const sortBy = params.get('sortBy') ?? 'LAST_NAME'
  const order = params.get('order') ?? 'ASC'
  const page = Number(params.get('page') ?? 0)
  const [form, setForm] = useState(filter)

  const { data, errors, loading } = useQuery(
    'findOwners',
    { lastName: filter.lastName || null, city: filter.city || null, page, size: SIZE, sortBy, order },
    { skip: !available('findOwners') },
  )

  if (!available('findOwners')) {
    return <FeaturePending featureKey="owners" />
  }

  const update = (changes) => setParams({ ...filter, sortBy, order, page: '0', ...changes })
  const result = data?.owners

  return (
    <div>
      <PageTitle actions={available('addOwner') && <ButtonLink to="/owners/new">Add owner</ButtonLink>}>Owners</PageTitle>
      <form
        className="mb-6 flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          update(form)
        }}
      >
        <input
          className={`${inputClass} w-48`}
          placeholder="Last name starts with"
          value={form.lastName}
          onChange={(event) => setForm({ ...form, lastName: event.target.value })}
        />
        <input
          className={`${inputClass} w-40`}
          placeholder="City"
          value={form.city}
          onChange={(event) => setForm({ ...form, city: event.target.value })}
        />
        <select className={`${inputClass} w-36`} value={sortBy} onChange={(event) => update({ sortBy: event.target.value })}>
          <option value="LAST_NAME">Last name</option>
          <option value="FIRST_NAME">First name</option>
          <option value="CITY">City</option>
        </select>
        <select className={`${inputClass} w-32`} value={order} onChange={(event) => update({ order: event.target.value })}>
          <option value="ASC">A to Z</option>
          <option value="DESC">Z to A</option>
        </select>
        <button className="rounded bg-spring-green px-4 py-2 text-sm text-white hover:bg-spring-dark-green">Find</button>
      </form>
      <Errors errors={errors} />
      {loading && <Loading />}
      {result && (
        <>
          <Table headers={['Name', 'Address', 'City']}>
            {result.content.map((owner) => (
              <tr key={owner.id}>
                <td className="px-3 py-2">
                  <Link to={`/owners/${owner.id}`} className="text-spring-dark-green hover:underline">
                    {personName(owner)}
                  </Link>
                </td>
                <td className="px-3 py-2">{owner.address}</td>
                <td className="px-3 py-2">{owner.city}</td>
              </tr>
            ))}
          </Table>
          <div className="mt-4 flex items-center justify-between text-sm text-spring-grey">
            <span>
              {result.totalElements} owners · page {result.currentPage + 1} of {Math.max(result.totalPages, 1)}
            </span>
            <span className="flex gap-2">
              <button
                disabled={!result.hasPrevious}
                onClick={() => setParams({ ...filter, sortBy, order, page: String(page - 1) })}
                className="rounded border px-3 py-1 disabled:opacity-40"
              >
                Previous
              </button>
              <button
                disabled={!result.hasNext}
                onClick={() => setParams({ ...filter, sortBy, order, page: String(page + 1) })}
                className="rounded border px-3 py-1 disabled:opacity-40"
              >
                Next
              </button>
            </span>
          </div>
        </>
      )}
    </div>
  )
}
