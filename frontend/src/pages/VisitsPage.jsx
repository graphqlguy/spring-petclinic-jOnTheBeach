import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Errors, FeaturePending, Loading, PageTitle, Table } from '../components/common'
import { inputClass, personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useQuery } from '../graphql/hooks'

function isoDate(daysFromToday) {
  const date = new Date()
  date.setDate(date.getDate() + daysFromToday)
  return date.toISOString().slice(0, 10)
}

export default function VisitsPage() {
  const { available } = useGraphQL()
  const [range, setRange] = useState({ from: isoDate(0), to: isoDate(30) })
  const { data, errors, loading } = useQuery('findVisits', range, { skip: !available('findVisits') })

  if (!available('findVisits')) {
    return <FeaturePending featureKey="agenda" />
  }
  return (
    <div>
      <PageTitle>Visits</PageTitle>
      <div className="mb-6 flex items-center gap-3 text-sm">
        <span className="text-spring-grey">From</span>
        <input
          type="date"
          className={`${inputClass} w-44`}
          value={range.from}
          onChange={(event) => setRange({ ...range, from: event.target.value })}
        />
        <span className="text-spring-grey">to</span>
        <input
          type="date"
          className={`${inputClass} w-44`}
          value={range.to}
          onChange={(event) => setRange({ ...range, to: event.target.value })}
        />
      </div>
      <Errors errors={errors} />
      {loading && <Loading />}
      {data &&
        (data.visits.length === 0 ? (
          <p className="text-sm text-spring-grey">No visits between these dates.</p>
        ) : (
          <Table headers={['Date', 'Pet', 'Owner', 'Vet', 'Description']}>
            {data.visits.map((visit) => (
              <tr key={visit.id}>
                <td className="px-3 py-2 whitespace-nowrap">{visit.date}</td>
                <td className="px-3 py-2">
                  {visit.pet.name} <span className="text-spring-grey">({visit.pet.type.name})</span>
                </td>
                <td className="px-3 py-2">
                  <Link to={`/owners/${visit.pet.owner.id}`} className="text-spring-dark-green hover:underline">
                    {personName(visit.pet.owner)}
                  </Link>
                </td>
                <td className="px-3 py-2">{personName(visit.vet)}</td>
                <td className="px-3 py-2">{visit.description}</td>
              </tr>
            ))}
          </Table>
        ))}
    </div>
  )
}
