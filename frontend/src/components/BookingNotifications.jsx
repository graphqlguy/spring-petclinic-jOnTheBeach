import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useSubscription } from '../graphql/hooks'

const SHOWN_FOR_MS = 10_000

// Shows each booking for ten seconds, on every page, once the backend has the visitBooked
// subscription of chapter 7.
export default function BookingNotifications() {
  const { available } = useGraphQL()
  const [visits, setVisits] = useState([])
  const close = useCallback((id) => setVisits((previous) => previous.filter((visit) => visit.id !== id)), [])
  useSubscription('visitBooked', (data) => setVisits((previous) => [...previous, data.visitBooked]), {
    skip: !available('visitBooked'),
  })

  if (visits.length === 0) {
    return null
  }
  return (
    <div className="fixed top-20 right-4 z-20 flex w-80 flex-col gap-2">
      {visits.map((visit) => (
        <Notification key={visit.id} visit={visit} onClose={close} />
      ))}
    </div>
  )
}

function Notification({ visit, onClose }) {
  // The ten seconds run while the tab is visible, so a booking made from another tab, such
  // as the MCP Inspector's, is still there when you come back.
  useEffect(() => {
    let timer
    const schedule = () => {
      clearTimeout(timer)
      if (document.visibilityState === 'visible') {
        timer = setTimeout(() => onClose(visit.id), SHOWN_FOR_MS)
      }
    }
    schedule()
    document.addEventListener('visibilitychange', schedule)
    return () => {
      clearTimeout(timer)
      document.removeEventListener('visibilitychange', schedule)
    }
  }, [visit.id, onClose])

  return (
    <div role="status" className="rounded border border-spring-green bg-white px-4 py-3 text-sm shadow-lg">
      <div className="mb-1 flex items-center justify-between">
        <span className="font-bold text-spring-dark-green">Visit booked</span>
        <button onClick={() => onClose(visit.id)} className="text-spring-grey hover:text-spring-brown" title="Close">
          ×
        </button>
      </div>
      <p>
        {visit.date} ·{' '}
        <Link to={`/owners/${visit.pet.owner.id}`} className="text-spring-dark-green hover:underline">
          {visit.pet.name} of {personName(visit.pet.owner)}
        </Link>
      </p>
      <p className="text-spring-grey">
        {visit.description}
        {visit.vet && ` · with ${personName(visit.vet)}`}
      </p>
    </div>
  )
}
