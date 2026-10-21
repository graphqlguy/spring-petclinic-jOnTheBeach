import { Link, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import HomePage from './pages/HomePage'
import LivePage from './pages/LivePage'
import LoginPage from './pages/LoginPage'
import OwnerDetailPage from './pages/OwnerDetailPage'
import OwnerFormPage from './pages/OwnerFormPage'
import OwnersPage from './pages/OwnersPage'
import PetFormPage from './pages/PetFormPage'
import SearchPage from './pages/SearchPage'
import VetDetailPage from './pages/VetDetailPage'
import VetsPage from './pages/VetsPage'
import VisitFormPage from './pages/VisitFormPage'
import VisitsPage from './pages/VisitsPage'

function NotFound() {
  return (
    <p>
      This page does not exist.{' '}
      <Link to="/" className="text-spring-dark-green underline">
        Go home
      </Link>
    </p>
  )
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/owners" element={<OwnersPage />} />
        <Route path="/owners/new" element={<OwnerFormPage />} />
        <Route path="/owners/:ownerId" element={<OwnerDetailPage />} />
        <Route path="/owners/:ownerId/edit" element={<OwnerFormPage />} />
        <Route path="/owners/:ownerId/pets/new" element={<PetFormPage />} />
        <Route path="/owners/:ownerId/pets/:petId/edit" element={<PetFormPage />} />
        <Route path="/owners/:ownerId/pets/:petId/visits/new" element={<VisitFormPage />} />
        <Route path="/vets" element={<VetsPage />} />
        <Route path="/vets/:vetId" element={<VetDetailPage />} />
        <Route path="/visits" element={<VisitsPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/live" element={<LivePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
