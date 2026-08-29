import { redirect } from 'next/navigation'

export default function HomePage() {
    // Executes instantly on the server
    redirect('/today')
}