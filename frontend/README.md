# PetClinic frontend

A React frontend for the GraphQL API the workshop builds. Start the backend first, then:

```bash
npm install    # the first time only
npm run dev
```

Open http://localhost:5173. If the backend runs on a port other than 8080, start the frontend with
`PETCLINIC_BACKEND=http://localhost:8090 npm run dev`.

## How it knows what to show

The frontend sends only the operations in `../src/main/resources/operations` and
`../src/main/resources/operations-ui`, one `.graphql` file each. At startup it reads the backend's
schema and validates every operation against it. A page lights up when its operations validate, so
each chapter of the workshop turns on another part of the UI. `src/features.js` lists which
operations each part needs.

Every request carries the operation's text and the SHA-256 hash of its file. The hash matters from
chapter 11 on, when the backend accepts the known operations by their hash alone.
