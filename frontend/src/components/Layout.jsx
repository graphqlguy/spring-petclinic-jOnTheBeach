import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useGraphQL } from '../context/GraphQLContext'
import BookingNotifications from './BookingNotifications'
import ChatPanel from './ChatPanel'

function NavItem({ to, children }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        `px-3 py-2 text-sm uppercase tracking-wide ${isActive ? 'bg-spring-green text-white' : 'text-spring-light-grey hover:bg-spring-green hover:text-white'}`
      }
    >
      {children}
    </NavLink>
  )
}

function SearchBox() {
  const [text, setText] = useState('')
  const navigate = useNavigate()
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (text.trim()) {
          navigate(`/search?q=${encodeURIComponent(text.trim())}`)
        }
      }}
    >
      <input
        value={text}
        onChange={(event) => setText(event.target.value)}
        placeholder="Search owners, pets, vets"
        className="w-52 rounded bg-white/10 px-3 py-1.5 text-sm text-white placeholder:text-spring-grey focus:outline-none"
      />
    </form>
  )
}

export default function Layout() {
  const { available } = useGraphQL()
  const { user, isStaff, signOut } = useAuth()
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b-4 border-spring-green bg-spring-brown">
        <nav className="mx-auto flex max-w-5xl flex-wrap items-center gap-1 px-4 py-2">
          <NavLink to="/" className="mr-4 flex items-center">
            <img src="/spring-logo.svg" alt="Spring" className="h-8" />
          </NavLink>
          <NavItem to="/">Home</NavItem>
          {available('findOwners') && <NavItem to="/owners">Find owners</NavItem>}
          {available('listVets') && <NavItem to="/vets">Veterinarians</NavItem>}
          {available('findVisits') && <NavItem to="/visits">Visits</NavItem>}
          <div className="ml-auto flex items-center gap-3">
            {available('search') && <SearchBox />}
            {user ? (
              <button onClick={signOut} className="text-sm text-spring-light-grey hover:text-white" title="Sign out">
                {user.name} {isStaff ? '(staff)' : ''} · sign out
              </button>
            ) : (
              available('login') && <NavItem to="/login">Sign in</NavItem>
            )}
          </div>
        </nav>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="bg-spring-brown py-4 text-center">
        <img src="/spring-logo.svg" alt="Spring" className="mx-auto h-6 opacity-70" />
      </footer>
      <BookingNotifications />
      <ChatPanel />
    </div>
  )
}
