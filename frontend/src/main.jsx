import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './context/AuthContext'
import { GraphQLProvider } from './context/GraphQLContext'
import './index.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <GraphQLProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </GraphQLProvider>
    </BrowserRouter>
  </StrictMode>,
)
