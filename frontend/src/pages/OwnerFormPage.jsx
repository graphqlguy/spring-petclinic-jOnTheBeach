import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Errors, FeaturePending, Field, Loading, PageTitle } from '../components/common'
import { fieldError, inputClass } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useMutation, useQuery } from '../graphql/hooks'

const FIELDS = [
  ['firstName', 'First name'],
  ['lastName', 'Last name'],
  ['address', 'Address'],
  ['city', 'City'],
  ['telephone', 'Telephone (10 digits)'],
]

function OwnerForm({ owner, onSubmit, state }) {
  const [values, setValues] = useState(() => Object.fromEntries(FIELDS.map(([name]) => [name, owner?.[name] ?? ''])))
  const fieldNames = new Set(FIELDS.map(([name]) => name))
  return (
    <form
      className="max-w-md"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit(values)
      }}
    >
      <Errors errors={state.errors?.filter((error) => !fieldNames.has(error.extensions?.field))} />
      {FIELDS.map(([name, label]) => (
        <Field key={name} label={label} error={fieldError(state.errors, name)}>
          <input
            className={`${inputClass} w-full`}
            value={values[name]}
            onChange={(event) => setValues({ ...values, [name]: event.target.value })}
          />
        </Field>
      ))}
      <Button disabled={state.loading}>{owner ? 'Update owner' : 'Add owner'}</Button>
    </form>
  )
}

export default function OwnerFormPage() {
  const { ownerId } = useParams()
  const editing = Boolean(ownerId)
  const { available } = useGraphQL()
  const navigate = useNavigate()
  const [save, state] = useMutation(editing ? 'updateOwner' : 'addOwner')
  const { data, loading } = useQuery('ownerSummary', { id: ownerId }, { skip: !editing || !available('ownerSummary') })

  // Adding needs addOwner and editing needs updateOwner, which chapter 3 adds one step apart
  if (!available(editing ? 'updateOwner' : 'addOwner')) {
    return <FeaturePending featureKey={editing ? 'ownerEdit' : 'ownerAdd'} />
  }
  if (editing && (loading || !data)) {
    return <Loading />
  }

  async function submit(values) {
    const result = await save(editing ? { id: ownerId, ...values } : values)
    const saved = result.data?.addOwner ?? result.data?.updateOwner
    if (saved && !result.errors) {
      navigate(`/owners/${saved.id}`)
    }
  }

  return (
    <div>
      <PageTitle>{editing ? 'Edit owner' : 'New owner'}</PageTitle>
      <OwnerForm owner={data?.owner} onSubmit={submit} state={state} />
    </div>
  )
}
