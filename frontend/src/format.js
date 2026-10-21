// Small helpers the pages share.

export const inputClass =
  'w-full rounded border border-spring-grey px-3 py-2 focus:border-spring-green focus:outline-none'

// The message of the error that names this input field, if the server sent one. The
// server names the field from chapter 4 on.
export function fieldError(errors, field) {
  return errors?.find((error) => error.extensions?.field === field)?.message
}

export function personName(person) {
  return person ? `${person.firstName} ${person.lastName}` : ''
}
