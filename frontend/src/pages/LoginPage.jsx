import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Errors, FeaturePending, Field, PageTitle } from '../components/common'
import { inputClass } from '../format'
import { useAuth } from '../context/AuthContext'
import { useGraphQL } from '../context/GraphQLContext'
import { useMutation } from '../graphql/hooks'

export default function LoginPage() {
  const { available } = useGraphQL()
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [login, state] = useMutation('login')
  const [values, setValues] = useState({ username: '', password: '' })

  if (!available('login')) {
    return <FeaturePending featureKey="login" />
  }

  async function submit(event) {
    event.preventDefault()
    const result = await login(values)
    if (result.data?.login) {
      signIn(result.data.login)
      navigate('/')
    }
  }

  return (
    <div>
      <PageTitle>Sign in</PageTitle>
      <form className="max-w-sm" onSubmit={submit}>
        <Errors errors={state.errors} />
        <Field label="Username">
          <input
            className={`${inputClass} w-full`}
            value={values.username}
            onChange={(event) => setValues({ ...values, username: event.target.value })}
          />
        </Field>
        <Field label="Password">
          <input
            type="password"
            className={`${inputClass} w-full`}
            value={values.password}
            onChange={(event) => setValues({ ...values, password: event.target.value })}
          />
        </Field>
        <Button disabled={state.loading}>Sign in</Button>
      </form>
      <p className="mt-6 text-sm text-spring-grey">
        <code>staff</code> / <code>staff</code> can change the clinic's data. <code>guest</code> / <code>guest</code>{' '}
        can only read it.
      </p>
    </div>
  )
}
