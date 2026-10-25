import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Errors, FeaturePending, Field, Loading, PageTitle } from '../components/common'
import { fieldError, inputClass, personName } from '../format'
import { useGraphQL } from '../context/GraphQLContext'
import { useMutation, useQuery } from '../graphql/hooks'

function PetForm({ pet, types, onSubmit, state }) {
  const [values, setValues] = useState({
    name: pet?.name ?? '',
    birthDate: pet?.birthDate ?? '',
    typeId: pet?.type?.id ?? types[0]?.id ?? '',
  })
  const set = (name) => (event) => setValues({ ...values, [name]: event.target.value })
  const fields = new Set(['name', 'birthDate', 'typeId'])
  return (
    <form
      className="max-w-md"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit(values)
      }}
    >
      <Errors errors={state.errors?.filter((error) => !fields.has(error.extensions?.field))} />
      <Field label="Name" error={fieldError(state.errors, 'name')}>
        <input className={`${inputClass} w-full`} value={values.name} onChange={set('name')} />
      </Field>
      <Field label="Birth date" error={fieldError(state.errors, 'birthDate')}>
        <input type="date" className={`${inputClass} w-full`} value={values.birthDate} onChange={set('birthDate')} />
      </Field>
      <Field label="Type" error={fieldError(state.errors, 'typeId')}>
        <select className={`${inputClass} w-full`} value={values.typeId} onChange={set('typeId')}>
          {types.map((type) => (
            <option key={type.id} value={type.id}>
              {type.name}
            </option>
          ))}
        </select>
      </Field>
      <Button disabled={state.loading}>{pet ? 'Update pet' : 'Add pet'}</Button>
    </form>
  )
}

export default function PetFormPage() {
  const { ownerId, petId } = useParams()
  const editing = Boolean(petId)
  const { available } = useGraphQL()
  const navigate = useNavigate()
  // Adding needs addPet and editing needs updatePet, which chapter 3 adds in two exercises
  const ready = available(editing ? 'updatePet' : 'addPet', 'listPetTypes', 'getOwnerDetails')
  const types = useQuery('listPetTypes', {}, { skip: !ready })
  const owner = useQuery('getOwnerDetails', { id: ownerId }, { skip: !ready })
  const [save, state] = useMutation(editing ? 'updatePet' : 'addPet')

  if (!ready) {
    return <FeaturePending featureKey={editing ? 'petEdit' : 'petAdd'} />
  }
  if (types.loading || owner.loading) {
    return <Loading />
  }
  const pet = editing ? owner.data?.owner?.pets.find((p) => p.id === petId) : null

  async function submit(values) {
    const variables = { ...values, birthDate: values.birthDate || null }
    const result = await save(editing ? { id: petId, ...variables } : { ownerId, ...variables })
    if ((result.data?.addPet || result.data?.updatePet) && !result.errors) {
      navigate(`/owners/${ownerId}`)
    }
  }

  return (
    <div>
      <PageTitle>
        {editing ? 'Edit pet' : 'New pet'}
        <span className="ml-3 text-base text-spring-grey normal-case">of {personName(owner.data?.owner)}</span>
      </PageTitle>
      <Errors errors={types.errors ?? owner.errors} />
      {types.data && (!editing || pet) && (
        <PetForm pet={pet} types={types.data.petTypes} onSubmit={submit} state={state} />
      )}
    </div>
  )
}
