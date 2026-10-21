import { useState } from 'react'
import { useGraphQL } from '../context/GraphQLContext'
import { tokenStore } from '../graphql/client'

// The clinic assistant from chapter 12. It sends the question to /api/chat with the
// signed-in user's token, so the agent's tools act as that user.
export default function ChatPanel() {
  const { chat } = useGraphQL()
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([])
  const [question, setQuestion] = useState('')
  const [waiting, setWaiting] = useState(false)

  if (!chat) {
    return null
  }

  async function ask(event) {
    event.preventDefault()
    const text = question.trim()
    if (!text || waiting) {
      return
    }
    setQuestion('')
    setMessages((previous) => [...previous, { from: 'you', text }])
    setWaiting(true)
    const headers = { 'Content-Type': 'text/plain' }
    const token = tokenStore.get()
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
    try {
      const response = await fetch('/api/chat', { method: 'POST', headers, body: text })
      const answer = await response.text()
      setMessages((previous) => [
        ...previous,
        { from: 'assistant', text: response.ok ? answer : `HTTP ${response.status}: ${answer}` },
      ])
    } catch (error) {
      setMessages((previous) => [...previous, { from: 'assistant', text: `The backend did not answer: ${error.message}` }])
    } finally {
      setWaiting(false)
    }
  }

  return (
    <div className="fixed right-4 bottom-4 z-10 flex flex-col items-end">
      {open && (
        <div className="mb-3 flex h-[28rem] w-80 flex-col rounded border border-spring-grey bg-white shadow-lg sm:w-96">
          <div className="bg-spring-brown px-4 py-2 text-sm text-spring-light-grey">Clinic assistant</div>
          <div className="flex-1 space-y-3 overflow-y-auto p-3 text-sm">
            {messages.length === 0 && (
              <p className="text-spring-grey">Ask about owners, pets, vets and visits, or ask it to book a visit.</p>
            )}
            {messages.map((message, i) => (
              <p
                key={i}
                className={`whitespace-pre-wrap rounded px-3 py-2 ${message.from === 'you' ? 'ml-8 bg-spring-light-grey' : 'mr-8 bg-spring-green/15'}`}
              >
                {message.text}
              </p>
            ))}
            {waiting && <p className="text-spring-grey">Thinking…</p>}
          </div>
          <form onSubmit={ask} className="flex border-t border-spring-grey">
            <input
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Which visits are booked next week?"
              className="flex-1 px-3 py-2 text-sm focus:outline-none"
            />
            <button className="px-4 text-sm text-spring-dark-green" disabled={waiting}>
              Send
            </button>
          </form>
        </div>
      )}
      <button
        onClick={() => setOpen(!open)}
        className="rounded-full bg-spring-green px-5 py-3 text-sm text-white shadow-lg hover:bg-spring-dark-green"
      >
        {open ? 'Close' : 'Ask the assistant'}
      </button>
    </div>
  )
}
