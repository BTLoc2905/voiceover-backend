import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import TouristApp from './VoiceMapScreens.jsx'
import AdminPortal from './AdminPortal.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {window.location.pathname.startsWith('/admin') || window.location.pathname === '/poimanagement'
      ? <AdminPortal />
      : <TouristApp />}
  </StrictMode>,
)
