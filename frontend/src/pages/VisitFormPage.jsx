import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Errors, FeaturePending, Field, Loading, PageTitle } from '../components/common'
import { fieldError, inputClass, personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useMutation, useQuery } from '../graphql/hooks'

function tomorrow() {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return date.toISOString().slice(0, 10)
}

export default function VisitFormPage() {
  const { ownerId, petId } = useParams()
  const { available } = useGraphQL()
  const navigate = useNavigate()
  const ready = available('addVisit', 'listVets', 'getOwnerDetails')
  const vets = useQuery('listVets', {}, { skip: !ready })
  const owner = useQuery('getOwnerDetails', { id: ownerId }, { skip: !ready })
  const [book, state] = useMutation('addVisit')
  const [values, setValues] = useState({ date: tomorrow(), vetId: '', description: '' })

  if (!ready) {
    return <FeaturePending featureKey="visitForm" />
  }
  if (vets.loading || owner.loading) {
    return <Loading />
  }
  const pet = owner.data?.owner?.pets.find((p) => p.id === petId)
  const set = (name) => (event) => setValues({ ...values, [name]: event.target.value })
  const fields = new Set(['date', 'vetId', 'description'])

  async function submit(event) {
    event.preventDefault()
    const result = await book({
      petId,
      vetId: values.vetId || null,
      date: values.date || null,
      description: values.description,
    })
    if (result.data?.addVisit && !result.errors) {
      navigate(`/owners/${ownerId}`)
    }
  }

  return (
    <div>
      <PageTitle>
        New visit
        <span className="ml-3 text-base text-spring-grey normal-case">
          for {pet?.name} of {personName(owner.data?.owner)}
        </span>
      </PageTitle>
      <Errors errors={vets.errors ?? owner.errors} />
      <form className="max-w-md" onSubmit={submit}>
        <Errors errors={state.errors?.filter((error) => !fields.has(error.extensions?.field))} />
        <Field label="Date" error={fieldError(state.errors, 'date')}>
          <input type="date" className={inputClass} value={values.date} onChange={set('date')} />
        </Field>
        <Field label="Vet" error={fieldError(state.errors, 'vetId')}>
          <select className={inputClass} value={values.vetId} onChange={set('vetId')}>
            <option value="">No vet yet</option>
            {vets.data?.vets.map((vet) => (
              <option key={vet.id} value={vet.id}>
                {personName(vet)}
                {vet.specialties.length > 0 && ` (${vet.specialties.map((s) => s.name).join(', ')})`}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Description" error={fieldError(state.errors, 'description')}>
          <input className={inputClass} value={values.description} onChange={set('description')} />
        </Field>
        <Button disabled={state.loading}>Book visit</Button>
      </form>
    </div>
  )
}
