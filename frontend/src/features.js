// What each part of the frontend needs from the backend, and the chapter that builds it.
// A feature is live once every one of its operations validates against the schema.
export const FEATURES = [
  { key: 'owners', title: 'Find owners', chapter: 1, operations: ['findOwners'], needs: 'the owners query' },
  { key: 'ownerPage', title: 'Owner page', chapter: 1, operations: ['ownerSummary'], needs: 'the owner query' },
  { key: 'vets', title: 'Vets', chapter: 1, operations: ['listVets'], needs: 'the vets query (exercise 1)' },
  { key: 'vetPage', title: 'Vet page', chapter: 1, operations: ['vetSummary'], needs: 'the vet query (exercise 2)' },
  { key: 'ownerPets', title: 'Pets on the owner page', chapter: 2, operations: ['ownerPets'], needs: 'the pets of an owner' },
  {
    key: 'ownerDetails',
    title: 'Visits on the owner page',
    chapter: 2,
    operations: ['getOwnerDetails'],
    needs: 'the visits of a pet and the vet of a visit (exercise 1)',
  },
  { key: 'vetVisits', title: 'Visits on the vet page', chapter: 2, operations: ['vetVisits'], needs: 'the visits of a vet' },
  {
    key: 'vetDetails',
    title: 'Pets and owners on the vet page',
    chapter: 2,
    operations: ['getVetDetails'],
    needs: 'the pet of a visit and the owner of a pet',
  },
  { key: 'ownerAdd', title: 'Add owners', chapter: 3, operations: ['addOwner'], needs: 'the addOwner mutation' },
  { key: 'ownerEdit', title: 'Edit owners', chapter: 3, operations: ['updateOwner'], needs: 'the updateOwner mutation' },
  { key: 'visitForm', title: 'Book a visit', chapter: 3, operations: ['addVisit', 'listVets'], needs: 'the addVisit mutation' },
  {
    key: 'petAdd',
    title: 'Add pets',
    chapter: 3,
    operations: ['addPet', 'listPetTypes'],
    needs: 'the addPet mutation and the petTypes query (exercise 1)',
  },
  { key: 'petEdit', title: 'Edit pets', chapter: 3, operations: ['updatePet', 'listPetTypes'], needs: 'the updatePet mutation (exercise 2)' },
  { key: 'agenda', title: 'Visits agenda', chapter: 3, operations: ['findVisits'], needs: 'the visits query and the Date scalar' },
  { key: 'login', title: 'Sign in', chapter: 5, operations: ['login'], needs: 'the login mutation' },
  { key: 'search', title: 'Search', chapter: 6, operations: ['search'], needs: 'the search query and its union' },
  { key: 'notifications', title: 'Booking notifications', chapter: 7, operations: ['visitBooked'], needs: 'the visitBooked subscription' },
]

export const feature = (key) => FEATURES.find((f) => f.key === key)
